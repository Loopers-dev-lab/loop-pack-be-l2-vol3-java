package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventStatus;
import jakarta.persistence.*;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
public class OutboxEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "partition_key", nullable = false)
    private String partitionKey;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxEventStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private ZonedDateTime nextAttemptAt;

    @Column(name = "occurred_at", nullable = false)
    private ZonedDateTime occurredAt;

    @Column(name = "published_at")
    private ZonedDateTime publishedAt;

    @Column(name = "acked_at")
    private ZonedDateTime ackedAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    protected OutboxEventEntity() {
    }

    public static OutboxEventEntity from(OutboxEvent outboxEvent) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.id = outboxEvent.id();
        entity.eventId = outboxEvent.eventId();
        entity.eventType = outboxEvent.eventType();
        entity.aggregateType = outboxEvent.aggregateType();
        entity.aggregateId = outboxEvent.aggregateId();
        entity.topic = outboxEvent.topic();
        entity.partitionKey = outboxEvent.partitionKey();
        entity.payloadJson = outboxEvent.payloadJson();
        entity.status = outboxEvent.status();
        entity.attemptCount = outboxEvent.attemptCount();
        entity.nextAttemptAt = outboxEvent.nextAttemptAt();
        entity.occurredAt = outboxEvent.occurredAt();
        entity.publishedAt = outboxEvent.publishedAt();
        entity.ackedAt = outboxEvent.ackedAt();
        return entity;
    }

    public OutboxEvent toDomain() {
        return new OutboxEvent(id, eventId, eventType, aggregateType, aggregateId, topic, partitionKey, payloadJson, status, attemptCount, nextAttemptAt, occurredAt, publishedAt, ackedAt);
    }

    public void markPublished(ZonedDateTime publishedAt) {
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = publishedAt;
        this.lastError = null;
    }

    public void markAcked(ZonedDateTime ackedAt) {
        this.status = OutboxEventStatus.ACKED;
        this.ackedAt = ackedAt;
        this.lastError = null;
    }

    public boolean markProcessing(ZonedDateTime nextAttemptAt) {
        if (this.status == OutboxEventStatus.PUBLISHED || this.status == OutboxEventStatus.ACKED) {
            return false;
        }
        this.status = OutboxEventStatus.PROCESSING;
        this.nextAttemptAt = nextAttemptAt;
        return true;
    }

    public void markFailed(String reason, ZonedDateTime nextAttemptAt) {
        this.status = OutboxEventStatus.FAILED;
        this.attemptCount = this.attemptCount + 1;
        this.lastError = reason;
        this.nextAttemptAt = nextAttemptAt;
    }
}
