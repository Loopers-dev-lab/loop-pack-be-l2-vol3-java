package com.loopers.domain.coupon;

public class CouponEvent {

    public record IssueRequested(String requestId, Long couponId, Long userId) {}
}
