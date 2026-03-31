package com.loopers.application.event;

import com.loopers.domain.outbox.Outbox;
import com.loopers.domain.outbox.OutboxEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class UserBehaviorLoggingListener {

    @Async("outboxPublishExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOutboxEvent(OutboxEvent event) {
        Outbox outbox = event.getOutbox();
        log.info("[UserBehavior] type={}, partitionKey={}, payload={}",
                outbox.getEventType(), outbox.getPartitionKey(), outbox.getPayload());
    }
}
