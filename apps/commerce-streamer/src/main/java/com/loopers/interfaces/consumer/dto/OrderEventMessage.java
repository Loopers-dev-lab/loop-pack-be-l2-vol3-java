package com.loopers.interfaces.consumer.dto;

import com.loopers.domain.event.model.EventType;

import java.time.LocalDateTime;

public record OrderEventMessage(
        String eventId,
        EventType eventType,
        Long orderId,
        Long memberId,
        int totalPrice,
        long version,
        LocalDateTime createdAt
) {
}
