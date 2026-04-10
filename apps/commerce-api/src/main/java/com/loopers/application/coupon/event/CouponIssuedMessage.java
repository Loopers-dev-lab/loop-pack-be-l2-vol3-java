package com.loopers.application.coupon.event;

public record CouponIssuedMessage(
        Long couponId,
        Long userId,
        long issuedAt
) {}
