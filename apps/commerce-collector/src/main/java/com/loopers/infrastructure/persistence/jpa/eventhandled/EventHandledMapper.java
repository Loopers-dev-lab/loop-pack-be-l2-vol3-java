package com.loopers.infrastructure.persistence.jpa.eventhandled;

import com.loopers.domain.eventhandled.EventHandled;

/**
 * EventHandled 도메인 객체와 JPA 엔티티 간 변환을 담당.
 */
public class EventHandledMapper {

    private EventHandledMapper() {}

    public static EventHandled toDomain(EventHandledJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return EventHandled.reconstitute(
            entity.getId(),
            entity.getEventId(),
            entity.getEventType(),
            entity.getHandledAt()
        );
    }

    public static EventHandledJpaEntity toJpaEntity(EventHandled domain) {
        if (domain == null) {
            return null;
        }
        return new EventHandledJpaEntity(
            domain.getEventId(),
            domain.getEventType()
        );
    }
}
