package com.loopers.support.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_pending", columnList = "status, created_at")
})
@Getter
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 50)
    private String aggregateId;

    @Column(name = "payload", nullable = false, columnDefinition = "JSON")
    private String payload;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxEventStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries;

    @Column(name = "next_retry_at")
    private ZonedDateTime nextRetryAt;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "sent_at")
    private ZonedDateTime sentAt;

    protected OutboxEvent() {
    }

    private OutboxEvent(String eventId, String eventType, String aggregateType, String aggregateId,
                        String payload, String topic) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.topic = topic;
        this.status = OutboxEventStatus.PENDING;
        this.retryCount = 0;
        this.maxRetries = 5;
        this.nextRetryAt = null;
        this.createdAt = ZonedDateTime.now();
    }

    public static OutboxEvent create(String eventId, String eventType, String aggregateType,
                                     String aggregateId, String payload, String topic) {
        return new OutboxEvent(eventId, eventType, aggregateType, aggregateId, payload, topic);
    }

    public void markSent() {
        this.status = OutboxEventStatus.SENT;
        this.sentAt = ZonedDateTime.now();
    }

    public void markFailed() {
        this.status = OutboxEventStatus.FAILED;
    }

    public void scheduleNextRetry() {
        this.retryCount++;
        long delayMinutes = Math.min((long) Math.pow(2, retryCount - 1), 30);
        this.nextRetryAt = ZonedDateTime.now().plusMinutes(delayMinutes);
    }

    public boolean isMaxRetriesExceeded() {
        return retryCount >= maxRetries;
    }

    public boolean isRetryable() {
        return nextRetryAt == null || !nextRetryAt.isAfter(ZonedDateTime.now());
    }
}
