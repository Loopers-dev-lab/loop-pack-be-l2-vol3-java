package com.loopers.infrastructure.outbox;

/**
 * Kafka로 전송되는 Outbox 메시지 엔벨로프.
 * Consumer에서 eventId(멱등성), eventType(라우팅), payload(데이터)를 사용한다.
 */
public record OutboxMessage(
        Long eventId,
        String aggregateType,
        Long aggregateId,
        String eventType,
        String payload
) {}
