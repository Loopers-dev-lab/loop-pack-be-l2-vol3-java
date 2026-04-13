package com.loopers.interfaces.consumer.dto;

import com.loopers.domain.event.model.EventType;

import java.time.LocalDateTime;
import java.util.List;

public record OrderEventMessage(
        String eventId,
        EventType eventType,
        Long orderId,
        Long memberId,
        int totalPrice,
        List<OrderProductInfo> orderProducts,
        long version,
        LocalDateTime createdAt
) {
    public record OrderProductInfo(Long productId, int price, int quantity) {
    }
}
