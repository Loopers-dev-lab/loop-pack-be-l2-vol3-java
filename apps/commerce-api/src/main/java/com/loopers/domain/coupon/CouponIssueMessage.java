package com.loopers.domain.coupon;

public record CouponIssueMessage(String requestId, Long couponId, Long userId) {}