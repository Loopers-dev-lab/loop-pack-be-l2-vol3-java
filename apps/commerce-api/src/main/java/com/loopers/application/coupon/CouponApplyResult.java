package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssue;

public record CouponApplyResult(
    Long couponIssueId,
    int discountAmount,
    CouponIssue couponIssue
) {}
