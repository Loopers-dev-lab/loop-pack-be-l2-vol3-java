package com.loopers.domain.event;

public record PaymentFailedEvent(Long orderId, Long userId, Long userCouponId, String reason) {
}
