package com.loopers.application.order;

import com.loopers.domain.order.event.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class OrderEventHandler {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("[ACTION_LOG] order_created orderId={} memberId={} totalAmount={} createdAt={}",
                event.orderId(), event.memberId(), event.totalAmount(), event.createdAt());
    }
}
