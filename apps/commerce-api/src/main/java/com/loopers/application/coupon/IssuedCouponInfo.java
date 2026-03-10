package com.loopers.application.coupon;

import com.loopers.domain.coupon.IssuedCoupon;

public record IssuedCouponInfo(
    Long id,
    Long couponId,
    Long userId,
    String status
) {
    public static IssuedCouponInfo from(IssuedCoupon issuedCoupon) {
        return new IssuedCouponInfo(
            issuedCoupon.getId(),
            issuedCoupon.getCouponId(),
            issuedCoupon.getUserId(),
            issuedCoupon.getStatus().name()
        );
    }
}
