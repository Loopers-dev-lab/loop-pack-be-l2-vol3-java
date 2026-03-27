package com.loopers.domain.event;

public record OrderExpiredEvent(Long orderId, Long userCouponId) {
}
