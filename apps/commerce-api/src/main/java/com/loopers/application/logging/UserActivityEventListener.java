package com.loopers.application.logging;

import com.loopers.domain.like.event.LikeToggledEvent;
import com.loopers.domain.order.event.OrderPlacedEvent;
import com.loopers.domain.payment.event.PaymentCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class UserActivityEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderPlaced(OrderPlacedEvent event) {
        log.info("[UserActivity] 주문 생성 - userId={}, orderId={}, amount={}",
            event.userId(), event.orderId(), event.totalAmountValue());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("[UserActivity] 결제 {} - userId={}, orderId={}, paymentId={}",
            event.success() ? "성공" : "실패", event.userId(), event.orderId(), event.paymentId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLikeToggled(LikeToggledEvent event) {
        log.info("[UserActivity] 좋아요 {} - productId={}",
            event.liked() ? "등록" : "취소", event.productId());
    }
}
