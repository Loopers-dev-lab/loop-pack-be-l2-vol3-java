package com.loopers.domain.coupon;

import java.time.ZonedDateTime;

public record ModifyCoupon(
        Long couponId,
        String name,
        Long discountValue,
        Long maxDiscountPrice,
        Long minOrderPrice,
        ZonedDateTime expiredAt
) {
}
