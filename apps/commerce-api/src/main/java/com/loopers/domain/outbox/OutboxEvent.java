package com.loopers.domain.outbox;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
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
@Table(name = "outbox_event", uniqueConstraints = {
        @UniqueConstraint(name = "uk_outbox_aggregate_version",
                columnNames = {"aggregate_id", "aggregate_type", "version"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class OutboxEvent {

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

    @Column
    private ZonedDateTime publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = ZonedDateTime.now();
    }

    /**
     * Outbox 이벤트를 생성한다.
     *
     * @param aggregateId   대상 엔티티 ID
     * @param aggregateType 도메인 타입 (예: "LIKE", "ORDER")
     * @param eventType     이벤트 종류 (예: "LIKED", "ORDER_PLACED")
     * @param payload       직렬화된 이벤트 데이터 (JSON)
     * @param topic         발행 대상 Kafka 토픽
     * @param partitionKey  Kafka 파티션 키
     * @param version       이벤트 버전 (같은 aggregate 내 발행 순번)
     * @return 생성된 Outbox 이벤트 (INIT 상태)
     */
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
        return event;
    }

    /**
     * Outbox 이벤트의 발행 상태.
     */
    public enum Status {
        INIT,
        PUBLISHED,
        PUBLISH_FAILED
    }
}
