package com.loopers.interfaces.consumer.dto;

import com.loopers.domain.event.model.EventType;

import java.time.LocalDateTime;

public record CatalogEventMessage(
        String eventId,
        EventType eventType,
        Long productId,
        Long memberId,
        long version,
        LocalDateTime createdAt
) {
}
