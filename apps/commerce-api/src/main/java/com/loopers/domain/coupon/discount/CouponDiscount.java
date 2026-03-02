package com.loopers.domain.coupon.discount;

import com.loopers.domain.shared.Money;

public record CouponDiscount(
        Money discountAmount,
        Long ownedCouponId
) {

    public static final CouponDiscount NONE = new CouponDiscount(Money.ZERO, null);
}
