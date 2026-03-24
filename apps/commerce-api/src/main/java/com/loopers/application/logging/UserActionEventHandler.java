package com.loopers.application.logging;

import com.loopers.domain.like.LikeEvent;
import com.loopers.domain.order.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class UserActionEventHandler {

    private final Logger log = LoggerFactory.getLogger(UserActionEventHandler.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(LikeEvent.Created event) {
        log.info("CREATE LIKE - userId: {}, productId: {}", event.userId(), event.productId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(LikeEvent.Deleted event) {
        log.info("DELETE LIKE - userId: {}, productId: {}", event.userId(), event.productId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderEvent.Created event) {
        log.info("CREATE ORDER - userId: {}, productId: {}, totalAmount: {}", event.userId(), event.orderId(), event.totalAmount());
    }
}
