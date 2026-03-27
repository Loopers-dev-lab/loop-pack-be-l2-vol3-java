package com.loopers.domain.coupon;

import org.springframework.test.util.ReflectionTestUtils;

public class OwnedCouponFixture {

    public static OwnedCoupon createOwnedCoupon(Coupon coupon, Long userId) {
        OwnedCoupon ownedCoupon = new OwnedCoupon();
        ReflectionTestUtils.setField(ownedCoupon, "coupon", coupon);
        ReflectionTestUtils.setField(ownedCoupon, "userId", userId);
        return ownedCoupon;
    }
}
