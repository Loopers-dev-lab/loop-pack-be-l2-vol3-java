package com.loopers.domain.outbox;

/**
 * Outbox 이벤트 상태.
 */
public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED
}
