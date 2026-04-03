package com.loopers.domain.coupon;

public enum CouponIssueRequestStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    FAILED_SOLD_OUT,
    FAILED_DUPLICATE_ISSUANCE,
    FAILED_EXPIRED,
    FAILED_COUPON_NOT_FOUND
}
