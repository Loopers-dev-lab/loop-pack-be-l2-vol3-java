package com.loopers.application.coupon;

import java.time.ZonedDateTime;

import com.loopers.domain.coupon.CouponType;

public class CouponCommand {

    public record CreateCouponCommand(
            String name,
            CouponType type,
            Long discountValue,
            Long maxDiscountPrice,
            Long minOrderPrice,
            ZonedDateTime expiredAt
    ) {
    }
}
