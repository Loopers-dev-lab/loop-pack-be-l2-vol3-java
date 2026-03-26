package com.loopers.application.payment;

import com.loopers.domain.event.PaymentCompletedEvent;
import com.loopers.domain.event.PaymentFailedEvent;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGateway.PaymentGatewayOrderResponse;
import com.loopers.domain.payment.PaymentGateway.PaymentGatewayRequest;
import com.loopers.domain.payment.PaymentGateway.PaymentGatewayResponse;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentFacade {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;
    private final PaymentTransactionService paymentTransactionService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${pg.callback-url:http://localhost:8080/api/v1/payments/callback}")
    private String callbackUrl;

    /**
     * 결제 요청: DB 저장(트랜잭션) → PG 호출(트랜잭션 밖) → 상태 업데이트(트랜잭션)
     *
     * PG 호출 실패/서킷 OPEN 시 fallback이 PENDING 응답을 반환하므로
     * Payment는 PENDING 상태를 유지하고, 스케줄러가 PG 조회 후 최종 상태를 확정한다.
     */
    public PaymentInfo requestPayment(Long orderId, Long userId, CardType cardType, String cardNo) {
        // 1. 주문 조회 + 결제 저장 + 주문 상태 변경 (트랜잭션 - 별도 Bean)
        Payment payment = paymentTransactionService.preparePayment(orderId, userId, cardType, cardNo);

        // 2. PG 호출 (트랜잭션 밖) — fallback 시 PENDING 응답 반환
        PaymentGatewayRequest pgRequest = new PaymentGatewayRequest(
                String.valueOf(orderId),
                cardType.name(),
                cardNo,
                payment.getAmount().longValue(),
                callbackUrl
        );

        PaymentGatewayResponse pgResponse =
                paymentGateway.requestPayment(String.valueOf(userId), pgRequest);

        // 3. PG 응답이 성공이면 즉시 완료 처리, PENDING이면 스케줄러에게 위임
        if (pgResponse.transactionKey() != null) {
            paymentTransactionService.completePayment(payment, pgResponse.transactionKey(), "결제 완료");
        } else {
            log.info("결제 처리 대기: orderId={}, reason={}", orderId, pgResponse.reason());
        }

        return PaymentInfo.from(payment);
    }

    /**
     * PG 콜백 처리
     */
    @Transactional
    public void handleCallback(Long orderId, String transactionKey, String status, String reason) {
        Payment payment = paymentService.getByOrderId(orderId);
        Order order = orderService.getById(orderId);

        if ("SUCCESS".equals(status)) {
            payment.markSuccess(transactionKey);
            order.completePayment();
            eventPublisher.publishEvent(new PaymentCompletedEvent(
                    orderId, payment.getUserId(), transactionKey, "콜백: 결제 완료"
            ));
        } else {
            payment.markFailed(reason);
            order.failPayment();
            eventPublisher.publishEvent(new PaymentFailedEvent(
                    orderId, payment.getUserId(), order.getUserCouponId(), "콜백: 결제 실패 - " + reason
            ));
        }
    }

    /**
     * 결제 조회
     */
    public PaymentInfo getPayment(Long orderId, Long userId) {
        Order order = orderService.getById(orderId);
        order.validateOwner(userId);
        Payment payment = paymentService.getByOrderId(orderId);
        return PaymentInfo.from(payment);
    }

    /**
     * 단건 수동 복구 (orderId 기반)
     */
    public void recoverPaymentByOrderId(Long orderId) {
        Payment payment = paymentService.getByOrderId(orderId);
        recoverPayment(payment);
    }

    /**
     * 미완료 결제건 복구
     */
    public void recoverPendingPayments() {
        List<Payment> pendingPayments = paymentService.getPendingPayments();

        for (Payment payment : pendingPayments) {
            try {
                recoverPayment(payment);
            } catch (Exception e) {
                log.warn("결제 복구 실패: paymentId={}, orderId={}, error={}",
                        payment.getId(), payment.getOrderId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void recoverPayment(Payment payment) {
        PaymentGatewayOrderResponse pgResponse = paymentGateway.getTransactionsByOrder(
                String.valueOf(payment.getUserId()),
                String.valueOf(payment.getOrderId())
        );

        if (pgResponse.transactions().isEmpty()) {
            handleNoTransactionFound(payment);
            return;
        }

        PaymentGatewayResponse latestTxn = pgResponse.transactions().get(pgResponse.transactions().size() - 1);
        Order order = orderService.getById(payment.getOrderId());

        if ("SUCCESS".equals(latestTxn.status()) && payment.getStatus() != PaymentStatus.SUCCESS) {
            payment.markSuccess(latestTxn.transactionKey());
            order.completePayment();
            eventPublisher.publishEvent(new PaymentCompletedEvent(
                    payment.getOrderId(), payment.getUserId(), latestTxn.transactionKey(), "스케줄러: 결제 복구 완료"
            ));
            log.info("결제 복구 성공: orderId={}", payment.getOrderId());
        } else if ("FAILED".equals(latestTxn.status()) && payment.getStatus() != PaymentStatus.FAILED) {
            payment.markFailed(latestTxn.reason());
            order.failPayment();
            eventPublisher.publishEvent(new PaymentFailedEvent(
                    payment.getOrderId(), payment.getUserId(), order.getUserCouponId(), "스케줄러: 결제 실패 확인 - " + latestTxn.reason()
            ));
            log.info("결제 실패 확인: orderId={}", payment.getOrderId());
        }
    }

    private static final long PENDING_TTL_MINUTES = 3;

    private void handleNoTransactionFound(Payment payment) {
        long minutesSinceCreated = java.time.Duration.between(
                payment.getCreatedAt(), java.time.ZonedDateTime.now()).toMinutes();

        if (minutesSinceCreated >= PENDING_TTL_MINUTES) {
            log.info("PENDING TTL 초과 → FAILED 처리: orderId={}, 경과={}분", payment.getOrderId(), minutesSinceCreated);
            Order order = orderService.getById(payment.getOrderId());
            String failReason = "결제 처리 시간 초과 (TTL " + PENDING_TTL_MINUTES + "분)";
            payment.markFailed(failReason);
            order.failPayment();
            eventPublisher.publishEvent(new PaymentFailedEvent(
                    payment.getOrderId(), payment.getUserId(), order.getUserCouponId(), "스케줄러: TTL 초과 실패 처리"
            ));
        } else {
            log.info("PG에 거래 내역 없음, TTL 대기 중: orderId={}, 경과={}분", payment.getOrderId(), minutesSinceCreated);
        }
    }
}
