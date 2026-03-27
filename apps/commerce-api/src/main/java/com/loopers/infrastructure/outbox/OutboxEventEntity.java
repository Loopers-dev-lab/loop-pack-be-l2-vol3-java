package com.loopers.infrastructure.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * Outbox 이벤트 Entity (인프라 전용 — 도메인 객체 아님)
 *
 * 비즈니스 TX 안에서 이벤트를 저장하고,
 * Relay가 주기적으로 PENDING 이벤트를 Kafka로 발행한다.
 */
@Entity
@Table(name = "outbox_event", indexes = {
        @Index(name = "idx_outbox_status_created", columnList = "status, created_at"),
        @Index(name = "idx_outbox_pending", columnList = "status, created_at",
               unique = false) // PENDING 조회 최적화
})
public class OutboxEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "JSON")
    private String payload;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(name = "partition_key", length = 100)
    private String partitionKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "published_at")
    private ZonedDateTime publishedAt;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    protected OutboxEventEntity() {
    }

    public static OutboxEventEntity create(String aggregateType, Long aggregateId,
                                            String eventType, String payload,
                                            String topic, String partitionKey) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.aggregateType = aggregateType;
        entity.aggregateId = aggregateId;
        entity.eventType = eventType;
        entity.payload = payload;
        entity.topic = topic;
        entity.partitionKey = partitionKey;
        entity.status = OutboxStatus.PENDING;
        entity.retryCount = 0;
        entity.createdAt = ZonedDateTime.now();
        return entity;
    }

    public void markProcessing() {
        this.status = OutboxStatus.PROCESSING;
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = ZonedDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.status = OutboxStatus.FAILED;
        this.retryCount++;
        this.errorMessage = errorMessage;
    }

    public void markRetry() {
        this.status = OutboxStatus.PENDING;
        this.retryCount++;
    }

    public Long getId() { return id; }
    public String getAggregateType() { return aggregateType; }
    public Long getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public String getTopic() { return topic; }
    public String getPartitionKey() { return partitionKey; }
    public OutboxStatus getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
    public ZonedDateTime getPublishedAt() { return publishedAt; }
    public String getErrorMessage() { return errorMessage; }
}
