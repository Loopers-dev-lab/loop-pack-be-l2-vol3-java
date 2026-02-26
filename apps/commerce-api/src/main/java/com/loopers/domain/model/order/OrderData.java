package com.loopers.domain.model.order;

import com.loopers.domain.model.user.UserId;

import java.time.LocalDateTime;
import java.util.List;

public record OrderData(
        Long id,
        UserId userId,
        List<OrderItem> items,
        OrderSnapshot snapshot,
        DeliveryInfo deliveryInfo,
        OrderAmount orderAmount,
        OrderStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
