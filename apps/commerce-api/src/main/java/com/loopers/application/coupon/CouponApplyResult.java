package com.loopers.application.coupon;

public record CouponApplyResult(
    Long couponIssueId,
    int discountAmount
) {}
