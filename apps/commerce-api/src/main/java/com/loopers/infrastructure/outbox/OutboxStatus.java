package com.loopers.infrastructure.outbox;

public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
