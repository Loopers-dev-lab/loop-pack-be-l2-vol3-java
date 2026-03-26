package com.loopers.interfaces.consumer;

/**
 * Outbox Relay가 Kafka로 전송하는 메시지 엔벨로프.
 * commerce-api의 OutboxMessage와 동일한 구조.
 */
public record OutboxMessage(
        Long eventId,
        String aggregateType,
        Long aggregateId,
        String eventType,
        String payload
) {}
