package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueResult;
import com.loopers.domain.coupon.CouponIssueResultStatus;

import java.time.ZonedDateTime;

public record CouponIssueResultInfo(
        Long id,
        Long userId,
        Long couponId,
        CouponIssueResultStatus status,
        String failureReason,
        ZonedDateTime createdAt
) {
    public static CouponIssueResultInfo from(CouponIssueResult result) {
        return new CouponIssueResultInfo(
                result.getId(),
                result.getUserId(),
                result.getCouponId(),
                result.getStatus(),
                result.getFailureReason(),
                result.getCreatedAt()
        );
    }
}
