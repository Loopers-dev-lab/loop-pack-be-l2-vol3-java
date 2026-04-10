package com.loopers.domain.coupon.event;

public record CouponIssueRequestedEvent(
    Long couponId,
    Long userId
) {
}
