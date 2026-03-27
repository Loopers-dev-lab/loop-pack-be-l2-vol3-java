package com.loopers.domain.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;

/**
 * Transactional Outbox Pattern
 *
 * 비즈니스 로직과 같은 트랜잭션 안에 이벤트를 저장한다.
 * 별도 스케줄러(OutboxPublisher)가 PENDING 상태를 읽어 Kafka로 발행한다.
 *
 * 왜 이게 필요한가?
 * - 비즈니스 TX 커밋 후 Kafka publish 전에 앱이 죽으면 이벤트 유실
 * - Outbox를 같은 TX로 묶으면 "저장됐으면 반드시 발행된다" 보장 (At Least Once)
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    public enum Status { PENDING, PUBLISHED, FAILED }

    @Id
    @Column(name = "event_id")
    private String eventId;  // UUID — Consumer 멱등성 키와 동일

    @Column(nullable = false)
    private String topic;

    @Column(name = "partition_key")
    private String partitionKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;  // JSON 직렬화된 이벤트

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "published_at")
    private ZonedDateTime publishedAt;

    protected OutboxEvent() {}

    public static OutboxEvent create(String eventId, String topic, String partitionKey, String payload) {
        OutboxEvent e = new OutboxEvent();
        e.eventId = eventId;
        e.topic = topic;
        e.partitionKey = partitionKey;
        e.payload = payload;
        e.status = Status.PENDING;
        e.createdAt = ZonedDateTime.now();
        return e;
    }

    public void markPublished() {
        this.status = Status.PUBLISHED;
        this.publishedAt = ZonedDateTime.now();
    }

    public void markFailed() {
        this.status = Status.FAILED;
    }

    public String getEventId() { return eventId; }
    public String getTopic() { return topic; }
    public String getPartitionKey() { return partitionKey; }
    public String getPayload() { return payload; }
    public Status getStatus() { return status; }
}
