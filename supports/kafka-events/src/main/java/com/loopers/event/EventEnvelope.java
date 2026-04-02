package com.loopers.event;

import java.time.Instant;

/**
 * Kafka 메시지 전송을 위한 이벤트 봉투.
 * Producer/Consumer 간 공유되는 이벤트 DTO.
 *
 * @param eventId       이벤트 고유 ID (UUID, 멱등성 키)
 * @param eventType     이벤트 타입 (LIKE_CREATED, ORDER_COMPLETED 등)
 * @param aggregateType 집계 타입 (LIKE, ORDER, PRODUCT)
 * @param aggregateId   대상 엔티티 ID
 * @param timestamp     이벤트 발생 시각
 * @param payload       JSON 직렬화된 이벤트 데이터
 */
public record EventEnvelope(
    String eventId,
    String eventType,
    String aggregateType,
    String aggregateId,
    Instant timestamp,
    String payload
) {

    public static EventEnvelope of(
        String eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        String payload
    ) {
        return new EventEnvelope(
            eventId,
            eventType,
            aggregateType,
            aggregateId,
            Instant.now(),
            payload
        );
    }
}
