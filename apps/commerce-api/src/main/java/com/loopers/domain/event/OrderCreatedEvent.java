package com.loopers.domain.event;

public record OrderCreatedEvent(Long orderId, Long memberId, int totalPrice) {
}
