package com.loopers.application.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderOutboxPayload(
        String eventId,
        String eventType,
        int schemaVersion,
        Long orderDbId,
        String orderId,
        Long memberId,
        BigDecimal totalAmount,
        LocalDateTime createdAt) {
}
