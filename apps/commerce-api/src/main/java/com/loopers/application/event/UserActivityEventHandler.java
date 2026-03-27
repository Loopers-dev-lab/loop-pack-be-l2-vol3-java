package com.loopers.application.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class UserActivityEventHandler {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleProductViewed(ProductViewedEvent event) {
        try {
            log.info("상품 조회: userId={}, productId={}", event.userId(), event.productId());
        } catch (Exception e) {
            log.error("상품 조회 로깅 실패: productId={}", event.productId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        try {
            log.info("결제 완료: paymentId={}, orderId={}, userId={}, amount={}",
                    event.paymentId(), event.orderId(), event.userId(), event.amount());
        } catch (Exception e) {
            log.error("결제 완료 로깅 실패: paymentId={}", event.paymentId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handlePaymentFailed(PaymentFailedEvent event) {
        try {
            log.info("결제 실패: paymentId={}, orderId={}, userId={}, reason={}",
                    event.paymentId(), event.orderId(), event.userId(), event.reason());
        } catch (Exception e) {
            log.error("결제 실패 로깅 실패: paymentId={}", event.paymentId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handlePaymentCanceled(PaymentCanceledEvent event) {
        try {
            log.info("결제 취소: paymentId={}, orderId={}, userId={}",
                    event.paymentId(), event.orderId(), event.userId());
        } catch (Exception e) {
            log.error("결제 취소 로깅 실패: paymentId={}", event.paymentId(), e);
        }
    }
}
