package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;

public record CouponIssueRequestInfo(
    String requestId,
    Long couponId,
    Long userId,
    String status,
    String failureReason
) {
    public static CouponIssueRequestInfo from(CouponIssueRequest request) {
        return new CouponIssueRequestInfo(
            request.getRequestId(),
            request.getCouponId(),
            request.getUserId(),
            request.getStatus().name(),
            request.getFailureReason()
        );
    }
}