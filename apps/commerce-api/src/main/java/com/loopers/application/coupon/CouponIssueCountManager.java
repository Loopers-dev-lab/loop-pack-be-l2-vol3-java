package com.loopers.application.coupon;

public interface CouponIssueCountManager {
    long increment(Long couponId);
    void decrement(Long couponId);
}
