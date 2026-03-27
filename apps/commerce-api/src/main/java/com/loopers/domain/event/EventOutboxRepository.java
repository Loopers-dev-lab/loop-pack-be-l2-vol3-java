package com.loopers.domain.event;

public interface EventOutboxRepository {
    EventOutbox save(EventOutbox outbox);
}
