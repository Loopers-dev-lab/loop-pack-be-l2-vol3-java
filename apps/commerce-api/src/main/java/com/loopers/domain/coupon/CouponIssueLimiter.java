package com.loopers.domain.coupon;

public interface CouponIssueLimiter {

    CouponIssueResult tryIssue(Long couponId, Long userId);

    void rollback(Long couponId, Long userId);

    void registerTotalQuantity(Long couponId, int totalQuantity);
}
