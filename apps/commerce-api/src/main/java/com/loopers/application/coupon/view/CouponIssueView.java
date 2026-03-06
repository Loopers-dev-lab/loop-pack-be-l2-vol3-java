package com.loopers.application.coupon.view;

import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.IssuedCoupon;

import java.time.LocalDateTime;
import java.util.UUID;

public record CouponIssueView(
        UUID couponId,
        String memberId,
        CouponStatus status,
        LocalDateTime issuedAt,
        LocalDateTime expiredAt,
        LocalDateTime usedAt
) {
    public static CouponIssueView from(IssuedCoupon issuedCoupon) {
        return new CouponIssueView(
                issuedCoupon.couponId(),
                issuedCoupon.memberId(),
                issuedCoupon.status(),
                issuedCoupon.issuedAt(),
                issuedCoupon.expiredAt(),
                issuedCoupon.usedAt()
        );
    }
}
