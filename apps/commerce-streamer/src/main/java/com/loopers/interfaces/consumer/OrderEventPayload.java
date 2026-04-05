package com.loopers.interfaces.consumer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderEventPayload(
        String eventId,
        String eventType,
        int schemaVersion,
        String orderId,
        Long memberId,
        BigDecimal totalAmount,
        LocalDateTime createdAt,
        List<OrderItemEventPayload> items
) {
}
