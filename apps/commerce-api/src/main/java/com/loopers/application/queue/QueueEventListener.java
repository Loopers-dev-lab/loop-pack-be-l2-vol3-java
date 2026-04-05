package com.loopers.application.queue;

import com.loopers.domain.order.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@RequiredArgsConstructor
@Component
public class QueueEventListener {

    private final QueueFacade queueFacade;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderEvent.Created event) {
        try {
            queueFacade.removeToken(event.userId());
        } catch (Exception e) {
            log.error("queue token removal failed after commit. userId={}", event.userId(), e);
        }
    }
}
