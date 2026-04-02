package com.loopers.infrastructure.event;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.event.EventHandled;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.ZonedDateTime;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Entity
@Table(
    name = "event_handled",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_event_handled_event_id_type", columnNames = {"event_id", "event_type"})
    },
    indexes = {
        @Index(name = "idx_event_handled_event_id", columnList = "event_id"),
        @Index(name = "idx_event_handled_entity_type_occurred", columnList = "entity_id, event_type, occurred_at")
    }
)
@NoArgsConstructor
public class EventHandledEntity extends BaseEntity {

    @Comment("이벤트 고유 ID")
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Comment("이벤트 타입")
    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Comment("처리 대상 엔티티 ID")
    @Column(name = "entity_id", nullable = false, updatable = false)
    private String entityId;

    @Comment("이벤트 발생 시각")
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private ZonedDateTime occurredAt;

    public EventHandledEntity(EventHandled eventHandled) {
        this.eventId = eventHandled.getEventId();
        this.eventType = eventHandled.getEventType();
        this.entityId = eventHandled.getEntityId();
        this.occurredAt = eventHandled.getOccurredAt();
    }

    public static EventHandled toDomain(EventHandledEntity entity) {
        return EventHandled.restore(
            entity.getId(),
            entity.eventId,
            entity.eventType,
            entity.entityId,
            entity.occurredAt
        );
    }
}
