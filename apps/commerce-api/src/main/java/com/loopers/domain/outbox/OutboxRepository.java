package com.loopers.domain.outbox;

public interface OutboxRepository {
    void append(OutboxEvent event);
}

