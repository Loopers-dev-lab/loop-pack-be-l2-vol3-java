package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueResultModel;
import com.loopers.domain.coupon.CouponIssueStatus;

import java.time.LocalDateTime;

public record CouponIssueInfo(
        String requestId,
        Long couponId,
        Long memberId,
        CouponIssueStatus status,
        String failReason,
        LocalDateTime processedAt
) {
    public static CouponIssueInfo from(CouponIssueResultModel model) {
        return new CouponIssueInfo(
                model.getRequestId(),
                model.getCouponId(),
                model.getMemberId(),
                model.getStatus(),
                model.getFailReason(),
                model.getProcessedAt()
        );
    }
}
