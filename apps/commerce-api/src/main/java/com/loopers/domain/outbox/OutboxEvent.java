package com.loopers.domain.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Outbox 이벤트 도메인 모델.
 * Transactional Outbox Pattern에서 이벤트를 저장하는 엔티티.
 */
public class OutboxEvent {

    private final Long id;
    private final String eventId;
    private final String eventType;
    private final String aggregateType;
    private final String aggregateId;
    private final String topic;
    private final String payload;
    private final Instant createdAt;
    private OutboxStatus status;
    private int retryCount;
    private Instant sentAt;

    private OutboxEvent(
        Long id,
        String eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        String topic,
        String payload,
        Instant createdAt,
        OutboxStatus status,
        int retryCount,
        Instant sentAt
    ) {
        this.id = id;
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        this.topic = Objects.requireNonNull(topic, "topic must not be null");
        this.payload = Objects.requireNonNull(payload, "payload must not be null");
        this.createdAt = createdAt;
        this.status = status;
        this.retryCount = retryCount;
        this.sentAt = sentAt;
    }

    public static OutboxEvent create(
        String eventType,
        String aggregateType,
        String aggregateId,
        String topic,
        String payload
    ) {
        return new OutboxEvent(
            null,
            UUID.randomUUID().toString(),
            eventType,
            aggregateType,
            aggregateId,
            topic,
            payload,
            Instant.now(),
            OutboxStatus.PENDING,
            0,
            null
        );
    }

    public static OutboxEvent reconstitute(
        Long id,
        String eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        String topic,
        String payload,
        Instant createdAt,
        OutboxStatus status,
        int retryCount,
        Instant sentAt
    ) {
        return new OutboxEvent(
            id,
            eventId,
            eventType,
            aggregateType,
            aggregateId,
            topic,
            payload,
            createdAt,
            status,
            retryCount,
            sentAt
        );
    }

    public void markAsSent() {
        this.status = OutboxStatus.SENT;
        this.sentAt = Instant.now();
    }

    public void markAsFailed() {
        this.status = OutboxStatus.FAILED;
        this.retryCount++;
    }

    public void incrementRetry() {
        this.retryCount++;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getTopic() {
        return topic;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
