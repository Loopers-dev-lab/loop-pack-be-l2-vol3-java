package com.loopers.infrastructure.outbox;

import com.loopers.support.outbox.DomainEvent;
import com.loopers.support.outbox.OutboxEvent;
import com.loopers.support.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventListener {

    private final OutboxEventFactory outboxEventFactory;
    private final OutboxEventRepository outboxEventRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onDomainEvent(DomainEvent event) {
        OutboxEvent outboxEvent = outboxEventFactory.create(event);
        if (outboxEvent == null) {
            return;
        }
        outboxEventRepository.save(outboxEvent);
    }
}
