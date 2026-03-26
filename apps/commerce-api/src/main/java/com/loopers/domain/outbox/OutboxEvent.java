package com.loopers.domain.outbox;

import java.time.Instant;

/**
 * Outbox에 저장할 이벤트 스냅샷(도메인 포트 파라미터).
 * 저장 자체는 infrastructure가 담당한다.
 */
public record OutboxEvent(
        String eventId,
        String topic,
        String partitionKey,
        String eventType,
        Instant occurredAt,
        String payload
) {
}

