package com.loopers.domain.coupon;

public record CouponIssueRequestInfo(
    Long requestId,
    Long couponId,
    Long memberId,
    CouponIssueRequestStatus status,
    String rejectReason
) {}
