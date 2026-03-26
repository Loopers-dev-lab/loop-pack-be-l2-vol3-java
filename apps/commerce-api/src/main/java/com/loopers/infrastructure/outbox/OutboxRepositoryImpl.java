package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxRepository;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRepositoryImpl implements OutboxRepository {

    private final OutboxJpaRepository outboxJpaRepository;

    public OutboxRepositoryImpl(OutboxJpaRepository outboxJpaRepository) {
        this.outboxJpaRepository = outboxJpaRepository;
    }

    @Override
    public void append(OutboxEvent event) {
        outboxJpaRepository.save(OutboxEventModel.pending(
                event.eventId(),
                event.topic(),
                event.partitionKey(),
                event.eventType(),
                event.occurredAt(),
                event.payload()));
    }
}

