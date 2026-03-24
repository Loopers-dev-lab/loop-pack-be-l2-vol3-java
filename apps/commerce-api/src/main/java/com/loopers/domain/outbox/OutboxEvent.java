package com.loopers.domain.outbox;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Transactional Outbox Pattern의 이벤트 엔티티.
 *
 * <p>도메인 이벤트를 같은 트랜잭션 내에서 Outbox 테이블에 저장하여,
 * DB 커밋과 메시지 발행의 원자성을 보장한다.</p>
 */
@Entity
@Table(name = "outbox_event",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_outbox_aggregate_version",
                        columnNames = {"aggregate_id", "aggregate_type", "version"})
        },
        indexes = {
                @Index(name = "idx_outbox_event_status_created_at", columnList = "status, created_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class OutboxEvent {

    private static final int MAX_RETRY_COUNT = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long aggregateId;

    @Column(nullable = false)
    private String aggregateType;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private String partitionKey;

    @Column(nullable = false)
    private int retryCount = 0;

    @Column
    private ZonedDateTime publishedAt;

    @Column
    private ZonedDateTime failedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    public static OutboxEvent create(
            Long aggregateId,
            String aggregateType,
            String eventType,
            String payload,
            String topic,
            String partitionKey,
            Long version
    ) {
        OutboxEvent event = new OutboxEvent();
        event.aggregateId = aggregateId;
        event.aggregateType = aggregateType;
        event.eventType = eventType;
        event.payload = payload;
        event.topic = topic;
        event.partitionKey = partitionKey;
        event.version = version;
        event.status = Status.INIT;
        event.createdAt = ZonedDateTime.now();
        return event;
    }

    public void publish() {
        this.status = Status.PUBLISHED;
        this.publishedAt = ZonedDateTime.now();
    }

    public void publishFail() {
        this.retryCount++;
        this.failedAt = ZonedDateTime.now();
        this.status = this.retryCount >= MAX_RETRY_COUNT ? Status.DEAD : Status.PUBLISH_FAILED;
    }

    public void dead() {
        this.status = Status.DEAD;
        this.failedAt = ZonedDateTime.now();
    }

    public boolean isDead() {
        return this.status == Status.DEAD;
    }

    /**
     * Outbox 이벤트의 발행 상태.
     */
    public enum Status {
        INIT,
        PUBLISHED,
        PUBLISH_FAILED,
        DEAD
    }
}
