package com.loopers.application.coupon;

import java.math.BigDecimal;

public record IssuedCouponSnapshot(
        Long issuedCouponId,
        BigDecimal discountAmount
) {
    private static final IssuedCouponSnapshot NONE = new IssuedCouponSnapshot(null, BigDecimal.ZERO);

    public static IssuedCouponSnapshot none() {
        return NONE;
    }

    public static IssuedCouponSnapshot of(Long issuedCouponId, BigDecimal discountAmount) {
        return new IssuedCouponSnapshot(issuedCouponId, discountAmount);
    }

    public boolean isApplied() {
        return issuedCouponId != null;
    }
}
