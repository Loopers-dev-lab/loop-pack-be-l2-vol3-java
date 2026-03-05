package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.IssuedCouponInfo;

public class CouponV1Dto {

    public record IssuedCouponResponse(Long couponId, String status) {
        public static IssuedCouponResponse from(IssuedCouponInfo info) {
            return new IssuedCouponResponse(info.couponId(), info.status());
        }
    }
}
