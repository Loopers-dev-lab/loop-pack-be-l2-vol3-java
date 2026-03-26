package com.loopers.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxRepository;
import com.loopers.domain.outbox.TransactionalOutboxWriter;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class TransactionalOutboxWriterImpl implements TransactionalOutboxWriter {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public TransactionalOutboxWriterImpl(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void record(String topic, String partitionKey, String eventType, Map<String, ?> payload) {
        try {
            outboxRepository.append(new OutboxEvent(
                    UUID.randomUUID().toString(),
                    topic,
                    partitionKey,
                    eventType,
                    Instant.now(),
                    objectMapper.writeValueAsString(payload)));
        } catch (Exception e) {
            throw new IllegalStateException("outbox payload serialization failed", e);
        }
    }
}
