package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponIssueInfo;

public class CouponIssueV1Dto {

    public record IssueResponse(
            String requestId,
            String status
    ) {}

    public record IssueResultResponse(
            String requestId,
            Long couponId,
            String status,
            String failReason
    ) {
        public static IssueResultResponse from(CouponIssueInfo info) {
            return new IssueResultResponse(
                    info.requestId(),
                    info.couponId(),
                    info.status().name(),
                    info.failReason()
            );
        }
    }
}
