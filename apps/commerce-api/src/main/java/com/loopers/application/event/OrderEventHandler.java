package com.loopers.application.event;

import com.loopers.domain.order.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class OrderEventHandler {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handle(OrderCreatedEvent event) {
        log.info("[UserBehavior] 주문 생성 - orderId={}, userId={}, totalAmount={}",
            event.orderId(), event.userId(), event.totalAmount());
    }
}
