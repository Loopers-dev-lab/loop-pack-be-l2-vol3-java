package com.loopers.domain.coupon;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record CreateCouponCommand(
        String name,
        CouponType type,
        BigDecimal value,
        BigDecimal minOrderAmount,
        ZonedDateTime expiredAt,
        Integer totalQuantity
) {}
