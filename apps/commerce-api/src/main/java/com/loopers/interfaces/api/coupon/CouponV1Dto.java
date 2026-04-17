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

    /**
     * 비동기 쿠폰 발급 상태 응답.
     * status: PENDING(아직 처리 안 됨), ISSUED(발급 완료)
     */
    public record CouponIssueStatusResponse(
        String status,
        CouponIssueResponse issue
    ) {
    }
}
