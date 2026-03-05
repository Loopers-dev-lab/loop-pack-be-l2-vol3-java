package com.loopers.application.coupon;

import java.time.ZonedDateTime;

import com.loopers.domain.coupon.CouponTerms;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.ModifyCoupon;

public class CouponCommand {

    public record CreateCouponCommand(
            String name,
            CouponType type,
            Long discountValue,
            Long maxDiscountPrice,
            Long minOrderPrice,
            ZonedDateTime expiredAt
    ) {

        public CouponTerms toCouponTerms() {
            return new CouponTerms(name, type, discountValue, maxDiscountPrice, minOrderPrice, expiredAt);
        }
    }

    public record UpdateCouponCommand(
            Long couponId,
            String name,
            Long discountValue,
            Long maxDiscountPrice,
            Long minOrderPrice,
            ZonedDateTime expiredAt
    ) {

        public ModifyCoupon toModifyCoupon() {
            return new ModifyCoupon(couponId, name, discountValue, maxDiscountPrice, minOrderPrice, expiredAt);
        }
    }
}
