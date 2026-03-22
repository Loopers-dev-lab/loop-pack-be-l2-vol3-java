package com.loopers.interfaces.consumer;

public record OrderEventPayload(
        String eventId,
        String eventType,
        int schemaVersion,
        Long orderDbId,
        String orderId,
        Long memberId,
        String totalAmount,
        String createdAt) {
}
