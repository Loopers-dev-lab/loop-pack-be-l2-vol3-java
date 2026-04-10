package com.loopers.domain.outbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEventModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Column(name = "partition_key", nullable = false, length = 100)
    private String partitionKey;

    @Column(name = "payload", nullable = false, columnDefinition = "JSON")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxEventStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /**
     * Outbox 이벤트 생성 팩토리.
     * Facade의 트랜잭션 안에서 호출하여 비즈니스 로직과 원자적으로 저장한다.
     */
    public static OutboxEventModel create(
            String aggregateType,
            String aggregateId,
            String eventType,
            String topic,
            String partitionKey,
            String payload
    ) {
        OutboxEventModel model = new OutboxEventModel();
        model.aggregateType = aggregateType;
        model.aggregateId = aggregateId;
        model.eventType = eventType;
        model.topic = topic;
        model.partitionKey = partitionKey;
        model.payload = payload;
        model.status = OutboxEventStatus.PENDING;
        model.retryCount = 0;
        model.createdAt = LocalDateTime.now();
        return model;
    }

    /** Kafka 발행 성공 시 호출 */
    public void markAsPublished() {
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    /**
     * Kafka 발행 실패 시 호출.
     * 최대 5회 재시도 후 DEAD 상태로 전이.
     */
    public void recordFailure(String error) {
        this.retryCount++;
        this.lastError = error;

        if (this.retryCount >= 5) {
            this.status = OutboxEventStatus.DEAD;
        } else {
            this.status = OutboxEventStatus.FAILED;
            this.nextRetryAt = LocalDateTime.now().plusSeconds((long) Math.pow(2, this.retryCount) * 10);
        }
    }

    /**
     * Kafka 발행 실패 시 지수 백오프(exponential backoff)를 적용하여 재시도 간격을 늘린다.
     * backoff: 3초 -> 9초 -> 27초 -> 81초 -> 243초.
     * 최대 5회 재시도 후 DEAD 상태로 전이.
     */
    public void recordFailureWithBackoff(String error) {
        this.retryCount++;
        this.lastError = error != null
            ? error.substring(0, Math.min(error.length(), 500))
            : "Unknown error";

        if (this.retryCount >= 5) {
            this.status = OutboxEventStatus.DEAD;
        } else {
            this.status = OutboxEventStatus.FAILED;
            // exponential backoff: 3초 → 9초 → 27초 → 81초 → 243초
            long delaySeconds = (long) Math.pow(3, this.retryCount);
            this.nextRetryAt = LocalDateTime.now().plusSeconds(delaySeconds);
        }
    }
}
