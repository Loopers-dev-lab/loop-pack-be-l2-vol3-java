package com.loopers.application.payment;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentCompletedEvent;
import com.loopers.domain.payment.PaymentEventPublisher;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.Quantity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * PG 응답 결과에 따라 Payment/Order 상태를 갱신하는 핸들러.
 *
 * PaymentEventListener에서 분리한 이유:
 * - @TransactionalEventListener(AFTER_COMMIT)은 트랜잭션 밖에서 실행됨
 * - 같은 클래스 내부 호출(self-invocation)은 AOP 프록시를 거치지 않아 @Transactional 무시
 * - 별도 빈으로 분리하면 프록시를 통한 정상적인 @Transactional 적용 가능
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentResultHandler {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final ProductService productService;
    private final CouponService couponService;
    private final PaymentEventPublisher paymentEventPublisher;

    // PG 접수 성공: transactionKey만 저장 (최종 결과는 콜백으로 수신)
    @Transactional
    public void handlePgAccepted(Long paymentId, String transactionKey) {
        Payment payment = paymentService.findById(paymentId);
        // dirty checking으로 transactionKey 저장
        payment.assignTransactionKey(transactionKey);
        log.info("PG 결제 접수 완료: paymentId={}, transactionKey={}", paymentId, transactionKey);
    }

    // PG 접수 실패 (retry 소진 후 서버 에러 확정): 결제 실패 + 주문 실패 + 재고 복구
    // WHERE status='PENDING' 조건부 UPDATE → 0건이면 이미 처리된 것 (콜백과 race 시 방어)
    @Transactional
    public void handlePgFailed(Long paymentId, Long orderId, String reason) {
        boolean updated = paymentService.markFailedIfPending(paymentId, null, reason);
        if (!updated) {
            log.info("이미 처리된 결제 (PG 접수 실패 처리 무시): paymentId={}", paymentId);
            return;
        }

        Order order = orderService.findById(orderId);
        order.markPaymentFailed();

        restoreStock(order);
        restoreCoupon(order);

        log.warn("PG 결제 접수 실패 → 재고/쿠폰 복구 완료: paymentId={}, orderId={}, reason={}",
                paymentId, orderId, reason);
    }

    // PG 응답 시간 초과 (EventListener에서 호출): 결과 불확실 → 상태 유지
    // PG에서 실제로 결제가 승인되었을 수 있으므로, 상태를 PENDING으로 유지하여
    // 폴링 스케줄러가 계속 PG 상태를 확인할 수 있게 한다.
    // 최종 타임아웃(5분) 시점에 PG 조회 후 결과 확정 → 그때 상태 전이 + 재고 복구
    public void handlePgResponseTimeout(Long paymentId, Long orderId, String reason) {
        log.warn("PG 결제 응답 시간 초과 (상태 유지, 폴링 대상): paymentId={}, orderId={}, reason={}",
                paymentId, orderId, reason);
    }

    // 최종 타임아웃 (5분 경과, 폴링 스케줄러에서 호출): 결과 확정 불가 → TIMEOUT + 재고 복구
    // WHERE status='PENDING' 조건부 UPDATE → 0건이면 콜백이 이미 처리한 것
    @Transactional
    public void handleFinalTimeout(Long paymentId, Long orderId, String reason) {
        boolean updated = paymentService.markTimeoutIfPending(paymentId);
        if (!updated) {
            log.info("이미 처리된 결제 (최종 타임아웃 처리 무시): paymentId={}", paymentId);
            return;
        }

        Order order = orderService.findById(orderId);
        order.markPaymentTimeout();

        restoreStock(order);
        restoreCoupon(order);

        log.warn("결제 최종 타임아웃 → 재고/쿠폰 복구 완료: paymentId={}, orderId={}, reason={}",
                paymentId, orderId, reason);
    }

    /**
     * PG 콜백 처리 (결제 최종 결과 수신).
     *
     * WHERE status='PENDING' 조건부 UPDATE로 멱등성 보장:
     * - UPDATE 성공(1건): 이 스레드가 처리 담당 → Order 상태 변경 + 재고 복구 진행
     * - UPDATE 실패(0건): 다른 스레드(또는 폴링)가 이미 처리 → 조용히 무시
     *
     * @return true: 처리됨, false: 이미 처리된 건 (무시)
     */
    @Transactional
    public boolean handleCallback(String transactionKey, String status, String failureReason) {
        Payment payment = paymentService.findByTransactionKey(transactionKey);

        if ("SUCCESS".equals(status)) {
            boolean updated = paymentService.markSuccessIfPending(payment.getId(), transactionKey);
            if (!updated) {
                log.info("이미 처리된 결제 콜백 무시 (SUCCESS): transactionKey={}", transactionKey);
                return false;
            }
            Order order = orderService.findById(payment.getOrderId());
            order.markPaid();

            // 결제 완료 이벤트 발행 (AFTER_COMMIT에서 알림/로깅 등 부가 처리)
            paymentEventPublisher.publish(new PaymentCompletedEvent(
                    payment.getId(), order.getId(), payment.getUserId(),
                    payment.getAmount(), transactionKey));

            log.info("결제 성공 콜백 처리: transactionKey={}, orderId={}", transactionKey, order.getId());

        } else if ("FAILED".equals(status)) {
            boolean updated = paymentService.markFailedIfPending(payment.getId(), transactionKey, failureReason);
            if (!updated) {
                log.info("이미 처리된 결제 콜백 무시 (FAILED): transactionKey={}", transactionKey);
                return false;
            }
            Order order = orderService.findById(payment.getOrderId());
            order.markPaymentFailed();
            restoreStock(order);
            restoreCoupon(order);
            log.warn("결제 실패 콜백 처리 → 재고/쿠폰 복구: transactionKey={}, orderId={}, reason={}",
                    transactionKey, order.getId(), failureReason);
        }

        return true;
    }

    // 쿠폰이 적용된 주문이면 쿠폰 사용 취소
    private void restoreCoupon(Order order) {
        if (order.getUserCouponId() == null) {
            return;
        }
        couponService.restoreCoupon(order.getUserCouponId(), order.getUserId());
        log.info("쿠폰 사용 취소 완료: orderId={}, userCouponId={}", order.getId(), order.getUserCouponId());
    }

    // 주문 항목 기반 재고 복구
    private void restoreStock(Order order) {
        Map<Long, Quantity> quantityByProductId = order.getOrderItems().stream()
                .collect(Collectors.toMap(
                        item -> item.getProductId(),
                        item -> item.getQuantity()
                ));

        List<Long> productIds = quantityByProductId.keySet().stream().sorted().toList();
        List<Product> products = productService.findAllByIds(productIds);

        for (Product product : products) {
            Quantity quantity = quantityByProductId.get(product.getId());
            product.increaseStock(quantity);
        }
    }
}
