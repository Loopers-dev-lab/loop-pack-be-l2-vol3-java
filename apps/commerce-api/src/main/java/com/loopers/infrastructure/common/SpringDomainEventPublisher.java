package com.loopers.infrastructure.common;

import com.loopers.domain.model.common.AggregateRoot;
import com.loopers.domain.model.common.DomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public SpringDomainEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publishEvents(AggregateRoot aggregateRoot) {
        aggregateRoot.getDomainEvents().forEach(eventPublisher::publishEvent);
        aggregateRoot.clearDomainEvents();
    }
}
