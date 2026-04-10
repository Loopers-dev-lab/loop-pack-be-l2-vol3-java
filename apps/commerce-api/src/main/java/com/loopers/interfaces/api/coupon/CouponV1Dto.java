package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponIssueInfo;
import com.loopers.domain.coupon.CouponIssueStatus;

public class CouponV1Dto {

    public record IssueResponse(
        Long requestId,
        Long couponId,
        Long userId,
        CouponIssueStatus status
    ) {
        public static IssueResponse from(CouponIssueInfo info) {
            return new IssueResponse(
                info.requestId(),
                info.couponId(),
                info.userId(),
                info.status()
            );
        }
    }
}
