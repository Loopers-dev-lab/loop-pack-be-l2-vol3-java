package com.loopers.interfaces.api.coupon;

import com.loopers.domain.coupon.CouponIssueModel;

import java.time.LocalDateTime;

public class CouponV1Dto {

    public record CouponIssueResponse(
        Long id,
        Long couponId,
        String status,
        LocalDateTime usedAt,
        Long orderId,
        LocalDateTime expiredAt
    ) {
        public static CouponIssueResponse from(CouponIssueModel issue) {
            return new CouponIssueResponse(
                issue.getId(),
                issue.couponId(),
                issue.status().name(),
                issue.usedAt(),
                issue.orderId(),
                issue.expiredAt()
            );
        }
    }
}
