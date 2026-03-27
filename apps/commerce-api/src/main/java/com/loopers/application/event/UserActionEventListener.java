package com.loopers.application.event;

import com.loopers.domain.event.LikeToggledEvent;
import com.loopers.domain.event.OrderCanceledEvent;
import com.loopers.domain.event.OrderCreatedEvent;
import com.loopers.domain.event.PaymentCompletedEvent;
import com.loopers.domain.event.ProductViewedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class UserActionEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("주문 생성 이벤트: orderId={}, userId={}, productIds={}",
                event.orderId(), event.userId(), event.productIds());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleOrderCanceled(OrderCanceledEvent event) {
        log.info("주문 취소 이벤트: orderId={}, userId={}, productIds={}",
                event.orderId(), event.userId(), event.productIds());
    }

    @EventListener
    @Async
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("결제 완료 이벤트: paymentId={}, orderId={}, userId={}",
                event.paymentId(), event.orderId(), event.userId());
    }

    @EventListener
    @Async
    public void handleProductViewed(ProductViewedEvent event) {
        log.info("상품 조회 이벤트: productId={}, userId={}", event.productId(), event.userId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleLikeToggled(LikeToggledEvent event) {
        log.info("좋아요 토글 이벤트: productId={}, userId={}, liked={}",
                event.productId(), event.userId(), event.liked());
    }
}
