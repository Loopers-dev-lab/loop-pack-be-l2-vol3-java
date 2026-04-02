package com.loopers.infrastructure.outbox;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.outbox.OutboxEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Entity
@Table(name = "outbox_event")
@NoArgsConstructor
public class OutboxEventEntity extends BaseEntity {

    @Comment("이벤트 타입")
    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Comment("이벤트 페이로드 (JSON)")
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String payload;

    @Comment("파티션 키 (순서 보장)")
    @Column(name = "partition_key", nullable = false, updatable = false)
    private String partitionKey;

    @Comment("Kafka 발행 시각 (null = 미발행)")
    @Column(name = "published_at")
    private ZonedDateTime publishedAt;

    public static OutboxEventEntity toEntity(OutboxEvent outboxEvent) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.eventType = outboxEvent.getEventType();
        entity.payload = outboxEvent.getPayload();
        entity.partitionKey = outboxEvent.getPartitionKey();
        return entity;
    }

    public static OutboxEvent toDomain(OutboxEventEntity entity) {
        return OutboxEvent.restore(
            entity.getId(),
            entity.eventType,
            entity.payload,
            entity.partitionKey,
            entity.publishedAt,
            entity.getCreatedAt()
        );
    }
}
