package com.loopers.kafka.event;

public record CatalogEvent(
    String eventId,       // 멱등성 키 (UUID)
    String eventType,     // LIKED, UNLIKED, VIEWED, ORDERED
    Long productId,
    Long memberId,
    long occurredAt,      // epoch millis
    Long price,           // ORDERED일 때만 사용 (단가)
    Integer quantity       // ORDERED일 때만 사용 (수량)
) {
    public enum Type {
        LIKED, UNLIKED, VIEWED, ORDERED
    }

    /**
     * LIKED, UNLIKED, VIEWED용 팩토리 (price/quantity 불필요)
     */
    public static CatalogEvent of(String eventId, Type type, Long productId, Long memberId, long occurredAt) {
        if (type == Type.ORDERED) {
            throw new IllegalArgumentException("ORDERED 이벤트는 ordered() 팩토리를 사용해야 합니다.");
        }
        return new CatalogEvent(eventId, type.name(), productId, memberId, occurredAt, null, null);
    }

    /**
     * ORDERED용 팩토리 (price/quantity 포함)
     */
    public static CatalogEvent ordered(String eventId, Long productId, Long memberId, long occurredAt, long price, int quantity) {
        return new CatalogEvent(eventId, Type.ORDERED.name(), productId, memberId, occurredAt, price, quantity);
    }
}
