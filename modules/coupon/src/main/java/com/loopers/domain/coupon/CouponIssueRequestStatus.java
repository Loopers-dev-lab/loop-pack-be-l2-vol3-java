package com.loopers.domain.coupon;

public enum CouponIssueRequestStatus {
    PENDING,
    ISSUED,
    REJECTED_DUPLICATE,
    REJECTED_EXPIRED,
    REJECTED_SOLD_OUT
}
