package com.loopers.application.payment;

import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponIssueRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.payment.*;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.infrastructure.pg.PgPaymentStatusResponse;
import com.loopers.infrastructure.pg.PgRouter;
import com.loopers.infrastructure.redis.StockReservationRedisRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 결제 복구 서비스 — 콜백 처리 + Polling Hybrid.
 *
 * <p>콜백 처리 흐름:</p>
 * <ol>
 *   <li>CallbackInbox에 원본 저장 (RECEIVED)</li>
 *   <li>조건부 UPDATE로 Payment 상태 전이</li>
 *   <li>SUCCESS → Order.pay() + Inbox PROCESSED</li>
 *   <li>FAILED → 재고 복원(Redis INCR + DB) + 쿠폰 복원 + Inbox PROCESSED</li>
 * </ol>
 *
 * <p>Polling Hybrid: PENDING/UNKNOWN 상태 결제건을 주기적으로 PG 확인</p>
 *
 * @see <a href="05-payment-resilience.md §8.5">Callback Inbox DLQ</a>
 * @see <a href="05-payment-resilience.md §8.4">Polling Hybrid</a>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRecoveryService {

    private final PaymentRepository paymentRepository;
    private final CallbackInboxRepository callbackInboxRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CouponIssueRepository couponIssueRepository;
    private final StockReservationRedisRepository stockRedisRepository;
    private final PgRouter pgRouter;

    /**
     * PG 콜백 처리.
     *
     * @param transactionKey PG 거래 키
     * @param pgStatus PG 상태 (SUCCESS, FAILED, PENDING 등)
     * @param payload 원본 콜백 데이터
     */
    @Transactional
    public void processCallback(String transactionKey, String pgStatus, String payload) {
        // 1. 콜백 원본 저장 (DLQ)
        PaymentModel payment = paymentRepository.findByTransactionKey(transactionKey).orElse(null);
        Long orderId = payment != null ? payment.getOrderId() : null;

        CallbackInbox inbox = callbackInboxRepository.save(
            CallbackInbox.create(transactionKey, orderId, pgStatus, payload));

        // 2. Payment 조회 실패 → 로그 + Inbox FAILED
        if (payment == null) {
            log.warn("콜백 수신 — Payment 없음: transactionKey={}", transactionKey);
            inbox.markFailed("Payment not found for transactionKey: " + transactionKey);
            callbackInboxRepository.save(inbox);
            return;
        }

        // 3. PENDING 콜백 → 무시 (06 §14.4 규칙)
        if ("PENDING".equals(pgStatus)) {
            log.info("PENDING 콜백 무시: paymentId={}", payment.getId());
            inbox.markProcessed();
            callbackInboxRepository.save(inbox);
            return;
        }

        // 4. 조건부 UPDATE로 상태 전이
        processPaymentTransition(payment, pgStatus, inbox);
    }

    private void processPaymentTransition(PaymentModel payment, String pgStatus, CallbackInbox inbox) {
        PaymentStatus targetStatus = "SUCCESS".equals(pgStatus) ? PaymentStatus.PAID : PaymentStatus.FAILED;
        List<PaymentStatus> allowedStatuses = List.of(PaymentStatus.PENDING, PaymentStatus.UNKNOWN);

        int affected = paymentRepository.updateStatusConditionally(
            payment.getId(), targetStatus, allowedStatuses);

        if (affected == 0) {
            log.info("조건부 UPDATE 미적용 (이미 처리된 건): paymentId={}, currentStatus={}",
                payment.getId(), payment.getStatus());
            inbox.markProcessed();
            callbackInboxRepository.save(inbox);
            return;
        }

        // 상태 전이 성공
        if (targetStatus == PaymentStatus.PAID) {
            handlePaymentSuccess(payment);
        } else {
            handlePaymentFailure(payment);
        }

        inbox.markProcessed();
        callbackInboxRepository.save(inbox);
        log.info("콜백 처리 완료: paymentId={}, newStatus={}", payment.getId(), targetStatus);
    }

    private void handlePaymentSuccess(PaymentModel payment) {
        Order order = orderRepository.findById(payment.getOrderId()).orElse(null);
        if (order != null) {
            order.pay();
            orderRepository.save(order);
            log.info("주문 결제 완료: orderId={}", order.getId());
        }
    }

    private void handlePaymentFailure(PaymentModel payment) {
        Order order = orderRepository.findById(payment.getOrderId()).orElse(null);
        if (order == null) return;

        // 재고 복원 (Redis INCR + DB)
        for (OrderItem item : order.getItems()) {
            stockRedisRepository.increase(item.getProductId(), item.getQuantity());
            productRepository.findById(item.getProductId()).ifPresent(product -> {
                product.increaseStock(item.getQuantity());
                productRepository.save(product);
            });
        }
        log.info("재고 복원 완료: orderId={}", order.getId());

        // 쿠폰 복원
        if (order.getCouponIssueId() != null) {
            couponIssueRepository.findById(order.getCouponIssueId()).ifPresent(couponIssue -> {
                couponIssue.cancelUse(ZonedDateTime.now());
                couponIssueRepository.save(couponIssue);
                log.info("쿠폰 복원 완료: couponIssueId={}", couponIssue.getId());
            });
        }
    }

    /**
     * Polling Hybrid — PENDING/UNKNOWN 상태 결제건을 PG에서 확인.
     *
     * <p>생성 후 10초 이상 경과한 PENDING 결제건만 폴링한다.</p>
     */
    @Scheduled(fixedRate = 10_000)
    public void checkPendingPayments() {
        List<PaymentModel> pendingPayments = paymentRepository.findAllByStatus(PaymentStatus.PENDING);
        List<PaymentModel> unknownPayments = paymentRepository.findAllByStatus(PaymentStatus.UNKNOWN);

        ZonedDateTime threshold = ZonedDateTime.now().minusSeconds(10);

        for (PaymentModel payment : pendingPayments) {
            if (payment.getCreatedAt() != null && payment.getCreatedAt().isBefore(threshold)) {
                pollPgStatus(payment);
            }
        }

        for (PaymentModel payment : unknownPayments) {
            pollPgStatus(payment);
        }
    }

    /**
     * 수동 복구 — 운영자가 PENDING/UNKNOWN 결제건의 PG 상태를 확인하여 확정.
     *
     * @see <a href="05-payment-resilience.md §10.3">수동 복구 API</a>
     */
    @Transactional
    public String manualConfirm(Long paymentId) {
        PaymentModel payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));

        if (payment.getStatus().isTerminal()) {
            return "이미 최종 상태입니다: " + payment.getStatus();
        }

        pollPgStatus(payment);

        // 재조회하여 변경된 상태 반환
        PaymentModel updated = paymentRepository.findById(paymentId).orElseThrow();
        return "확인 완료: " + updated.getStatus();
    }

    private void pollPgStatus(PaymentModel payment) {
        try {
            PgPaymentStatusResponse pgStatus;
            if (payment.getTransactionKey() != null && payment.getPgProvider() != null) {
                pgStatus = pgRouter.getPaymentStatus(
                    payment.getTransactionKey(), payment.getPgProvider());
            } else {
                pgStatus = pgRouter.getPaymentByOrderId(
                    String.valueOf(payment.getOrderId()),
                    pgRouter.getPrimaryClient().getProviderName());
            }

            if (pgStatus == null) return;

            switch (pgStatus.status()) {
                case "SUCCESS" -> processCallback(
                    payment.getTransactionKey(), "SUCCESS",
                    "polling-recovery");
                case "FAILED" -> processCallback(
                    payment.getTransactionKey(), "FAILED",
                    "polling-recovery: " + pgStatus.reason());
                default -> log.debug("PG 폴링 — 아직 처리 중: paymentId={}, pgStatus={}",
                    payment.getId(), pgStatus.status());
            }
        } catch (Exception e) {
            log.warn("PG 폴링 실패: paymentId={}, error={}", payment.getId(), e.getMessage());
        }
    }
}
