package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueResult;
import com.loopers.domain.coupon.CouponIssueStatus;

import java.time.ZonedDateTime;

public record CouponIssueResultInfo(
        Long id,
        Long couponTemplateId,
        CouponIssueStatus status,
        String rejectReason,
        ZonedDateTime createdAt
) {
    public static CouponIssueResultInfo from(CouponIssueResult result) {
        return new CouponIssueResultInfo(
                result.getId(),
                result.getCouponTemplateId(),
                result.getStatus(),
                result.getRejectReason(),
                result.getCreatedAt()
        );
    }
}
