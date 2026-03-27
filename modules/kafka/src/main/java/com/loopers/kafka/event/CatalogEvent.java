package com.loopers.kafka.event;

public record CatalogEvent(
    String eventId,       // 멱등성 키 (UUID)
    String eventType,     // LIKED, UNLIKED, VIEWED, ORDERED
    Long productId,
    Long memberId,
    long occurredAt       // epoch millis
) {
    public enum Type {
        LIKED, UNLIKED, VIEWED, ORDERED
    }
}
