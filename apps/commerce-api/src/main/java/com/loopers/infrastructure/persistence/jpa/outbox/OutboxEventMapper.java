package com.loopers.infrastructure.persistence.jpa.outbox;

import com.loopers.domain.outbox.OutboxEvent;

/**
 * OutboxEvent 도메인 객체와 JPA 엔티티 간 변환을 담당.
 */
public class OutboxEventMapper {

    private OutboxEventMapper() {}

    public static OutboxEvent toDomain(OutboxEventJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return OutboxEvent.reconstitute(
            entity.getId(),
            entity.getEventId(),
            entity.getEventType(),
            entity.getAggregateType(),
            entity.getAggregateId(),
            entity.getTopic(),
            entity.getPayload(),
            entity.getCreatedAt(),
            entity.getStatus(),
            entity.getRetryCount(),
            entity.getSentAt()
        );
    }

    public static OutboxEventJpaEntity toJpaEntity(OutboxEvent domain) {
        if (domain == null) {
            return null;
        }
        return new OutboxEventJpaEntity(
            domain.getEventId(),
            domain.getEventType(),
            domain.getAggregateType(),
            domain.getAggregateId(),
            domain.getTopic(),
            domain.getPayload(),
            domain.getStatus(),
            domain.getRetryCount()
        );
    }
}
