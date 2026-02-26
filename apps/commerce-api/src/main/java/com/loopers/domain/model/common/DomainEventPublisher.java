package com.loopers.domain.model.common;

public interface DomainEventPublisher {

    void publishEvents(AggregateRoot aggregateRoot);
}
