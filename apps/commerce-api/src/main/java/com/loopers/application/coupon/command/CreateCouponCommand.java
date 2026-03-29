package com.loopers.application.coupon.command;

import com.loopers.domain.coupon.CouponType;

import java.time.LocalDateTime;

public record CreateCouponCommand(
        String name,
        CouponType type,
        int value,
        int minOrderAmount,
        int totalQuantity,
        LocalDateTime expiredAt
) {
}
