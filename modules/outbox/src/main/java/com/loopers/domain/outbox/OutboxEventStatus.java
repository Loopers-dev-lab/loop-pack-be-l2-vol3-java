package com.loopers.domain.outbox;

public enum OutboxEventStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    ACKED,
    FAILED
}
