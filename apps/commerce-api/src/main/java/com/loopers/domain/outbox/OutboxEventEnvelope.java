package com.loopers.domain.outbox;

import java.time.ZonedDateTime;

/**
 * Kafka 메시지 payload에 담기는 이벤트 봉투.
 * eventId로 Consumer 멱등 처리, eventType으로 라우팅.
 */
public record OutboxEventEnvelope(
    String eventId,
    String eventType,
    ZonedDateTime occurredAt,
    Object data
) {
}
