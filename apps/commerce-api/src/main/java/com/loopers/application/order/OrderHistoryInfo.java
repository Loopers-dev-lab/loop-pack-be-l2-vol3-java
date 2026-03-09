package com.loopers.application.order;

import com.loopers.domain.order.OrderHistory;

import java.time.ZonedDateTime;

public record OrderHistoryInfo(
        Long id,
        Long orderId,
        String previousStatus,
        String newStatus,
        String description,
        ZonedDateTime createdAt
) {
    public static OrderHistoryInfo from(OrderHistory history) {
        return new OrderHistoryInfo(
                history.getId(),
                history.getOrderId(),
                history.getPreviousStatus() != null ? history.getPreviousStatus().name() : null,
                history.getNewStatus().name(),
                history.getDescription(),
                history.getCreatedAt()
        );
    }
}
