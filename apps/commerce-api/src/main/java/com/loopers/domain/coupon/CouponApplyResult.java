package com.loopers.domain.coupon;

import com.loopers.domain.common.Money;

public record CouponApplyResult(
    Long couponId,
    Money discountAmount
) {
    public static CouponApplyResult none() {
        return new CouponApplyResult(null, Money.ZERO);
    }

    public boolean isApplied() {
        return couponId != null;
    }
}
