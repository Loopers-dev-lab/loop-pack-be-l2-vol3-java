package com.loopers.collector.application;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * 상품 도메인(좋아요·집계 등) Kafka 메시지 공통 래퍼. Outbox 릴레이가 보내는 JSON과 동일 구조.
 */
public record ProductEventEnvelope(
        String eventId,
        String eventType,
        Instant occurredAt,
        String partitionKey,
        JsonNode data
) {
}
