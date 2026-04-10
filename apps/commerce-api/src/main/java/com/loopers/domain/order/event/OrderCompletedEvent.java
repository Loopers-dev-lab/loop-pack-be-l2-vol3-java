package com.loopers.domain.order.event;

public record OrderCompletedEvent(Long orderId, Long userId, int totalPrice) {
}
