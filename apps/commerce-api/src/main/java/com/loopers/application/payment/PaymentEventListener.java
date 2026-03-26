package com.loopers.application.payment;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.event.PaymentCompletedEvent;
import com.loopers.domain.event.PaymentFailedEvent;
import com.loopers.domain.order.OrderHistoryService;
import com.loopers.domain.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final OrderHistoryService orderHistoryService;
    private final CouponService couponService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        try {
            log.info("결제 완료 이벤트 수신: orderId={}, transactionKey={}", event.orderId(), event.transactionKey());
            orderHistoryService.recordHistory(
                    event.orderId(), OrderStatus.PAYMENT_PENDING, OrderStatus.PAID, event.message()
            );
        } catch (Exception e) {
            log.error("결제 완료 이벤트 처리 실패: orderId={}, transactionKey={}", event.orderId(), event.transactionKey(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentFailed(PaymentFailedEvent event) {
        try {
            log.info("결제 실패 이벤트 수신: orderId={}, reason={}", event.orderId(), event.reason());
            orderHistoryService.recordHistory(
                    event.orderId(), OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_FAILED, event.reason()
            );

            if (event.userCouponId() != null) {
                couponService.restoreUserCoupon(event.userCouponId());
            }
        } catch (Exception e) {
            log.error("결제 실패 이벤트 처리 실패: orderId={}, userCouponId={}", event.orderId(), event.userCouponId(), e);
        }
    }
}
