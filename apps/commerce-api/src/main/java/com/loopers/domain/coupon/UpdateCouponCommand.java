package com.loopers.domain.coupon;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record UpdateCouponCommand(
        String name,
        CouponType type,
        BigDecimal value,
        BigDecimal minOrderAmount,
        ZonedDateTime expiredAt
) {}
