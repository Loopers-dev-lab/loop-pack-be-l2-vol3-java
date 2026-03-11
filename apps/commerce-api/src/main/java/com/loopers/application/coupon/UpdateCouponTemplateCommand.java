package com.loopers.application.coupon;

import com.loopers.domain.coupon.DiscountType;
import java.time.ZonedDateTime;

public record UpdateCouponTemplateCommand(
    Long couponId,
    String name,
    DiscountType discountType,
    Integer discountValue,
    Integer minOrderAmount,
    ZonedDateTime expiredAt
) {
}

