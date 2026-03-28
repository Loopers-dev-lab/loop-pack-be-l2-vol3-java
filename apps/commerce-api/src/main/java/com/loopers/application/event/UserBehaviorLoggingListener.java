package com.loopers.application.event;

import com.loopers.application.like.event.LikeEvent;
import com.loopers.application.order.event.OrderCancelledEvent;
import com.loopers.application.order.event.OrderCreatedEvent;
import com.loopers.application.payment.event.PaymentCompletedEvent;
import com.loopers.application.payment.event.PaymentFailedEvent;
import com.loopers.application.product.event.ProductViewedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class UserBehaviorLoggingListener {

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleProductViewed(ProductViewedEvent event) {
        log.info("[UserBehavior] type=PRODUCT_VIEWED, userId={}, productId={}, timestamp={}",
            event.userId(), event.productId(), event.occurredAt());
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLike(LikeEvent event) {
        log.info("[UserBehavior] type={}, userId={}, productId={}, timestamp={}",
            event.action(), event.userId(), event.productId(), event.occurredAt());
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("[UserBehavior] type=ORDER_CREATED, userId={}, orderId={}, itemCount={}, timestamp={}",
            event.userId(), event.orderId(), event.items().size(), event.occurredAt());
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCancelled(OrderCancelledEvent event) {
        log.info("[UserBehavior] type=ORDER_CANCELLED, userId={}, orderId={}, timestamp={}",
            event.userId(), event.orderId(), event.occurredAt());
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("[UserBehavior] type=PAYMENT_COMPLETED, userId={}, orderId={}, paymentId={}, amount={}, timestamp={}",
            event.userId(), event.orderId(), event.paymentId(), event.amount(), event.occurredAt());
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentFailed(PaymentFailedEvent event) {
        log.info("[UserBehavior] type=PAYMENT_FAILED, userId={}, orderId={}, paymentId={}, timestamp={}",
            event.userId(), event.orderId(), event.paymentId(), event.occurredAt());
    }
}
