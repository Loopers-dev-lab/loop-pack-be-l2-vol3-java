package com.loopers.domain.coupon;

import java.time.ZonedDateTime;

public record CouponTerms(
        String name,
        CouponType type,
        Long discountValue,
        Long maxDiscountPrice,
        Long minOrderPrice,
        ZonedDateTime expiredAt
) {
}
