package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueStatus;

public record CouponIssueInfo(
    Long requestId,
    Long couponId,
    Long userId,
    CouponIssueStatus status
) {
    public static CouponIssueInfo from(CouponIssueRequest request) {
        return new CouponIssueInfo(
            request.getId(),
            request.getCouponId(),
            request.getUserId(),
            request.getStatus()
        );
    }
}
