package com.loopers.application.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderOutboxPayload(
        String eventId,
        String eventType,
        int schemaVersion,
        String orderId,
        Long memberId,
        BigDecimal totalAmount,
        LocalDateTime createdAt,
        List<OrderItemPayload> items
) {
}
