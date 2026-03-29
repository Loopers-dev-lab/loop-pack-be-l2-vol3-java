package com.loopers.application.coupon;

public interface CouponIssueMessagePublisher {
    void publish(CouponIssueMessage message);
}
