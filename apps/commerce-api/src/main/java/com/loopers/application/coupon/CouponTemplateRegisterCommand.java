package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponType;

import java.time.LocalDateTime;

public record CouponTemplateRegisterCommand(
        String name,
        CouponType type,
        int value,
        Integer minOrderAmount,
        LocalDateTime expiredAt
) {}
