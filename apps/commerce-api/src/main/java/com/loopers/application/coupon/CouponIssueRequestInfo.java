package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueStatus;

public record CouponIssueRequestInfo(
        Long requestId,
        Long couponId,
        Long userId,
        CouponIssueStatus status,
        String rejectReason
) {
    public static CouponIssueRequestInfo from(CouponIssueRequest request) {
        return new CouponIssueRequestInfo(
                request.getId(),
                request.getCouponId(),
                request.getUserId(),
                request.getStatus(),
                request.getRejectReason()
        );
    }
}
