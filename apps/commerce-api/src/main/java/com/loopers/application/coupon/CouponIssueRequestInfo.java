package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;

public record CouponIssueRequestInfo(
    Long requestId,
    Long couponId,
    Long userId,
    String status,
    String reason
) {
    public static CouponIssueRequestInfo from(CouponIssueRequest request) {
        return new CouponIssueRequestInfo(
            request.getId(),
            request.getCouponId(),
            request.getUserId(),
            request.getStatus().name(),
            request.getReason()
        );
    }
}
