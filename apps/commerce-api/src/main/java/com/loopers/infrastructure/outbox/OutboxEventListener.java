package com.loopers.infrastructure.outbox;

import com.loopers.support.outbox.DomainEvent;
import com.loopers.support.outbox.OutboxEvent;
import com.loopers.support.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventListener {

    private final OutboxEventFactory outboxEventFactory;
    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onDomainEvent(DomainEvent event) {
        OutboxEvent outboxEvent = outboxEventFactory.create(event);
        if (outboxEvent == null) {
            return;
        }

        // BEFORE_COMMIT: 같은 TX에서 Outbox INSERT (원자성 보장)
        outboxEventRepository.save(outboxEvent);

        // afterCommit: TX 커밋 후 즉시 발행 (비동기, 논블로킹)
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                kafkaTemplate.send(outboxEvent.getTopic(), outboxEvent.getAggregateId(), outboxEvent.getPayload())
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.warn("즉시 발행 실패, @Scheduled가 보완 예정: eventId={}", outboxEvent.getEventId(), ex);
                                // PENDING 상태 유지. 발행 쪽에서 DB 상태를 건드리지 않음.
                            }
                        });
            }
        });
    }
}
