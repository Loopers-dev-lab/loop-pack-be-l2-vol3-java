package com.loopers.domain.event;

public interface DomainEventPublisher {
    void publish(String aggregateType, String aggregateId, String eventType, Object payload, Object event);
}
