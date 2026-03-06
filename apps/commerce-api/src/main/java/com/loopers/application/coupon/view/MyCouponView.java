package com.loopers.application.coupon.view;

import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;

import java.time.LocalDateTime;
import java.util.UUID;

public record MyCouponView(
        UUID couponId,
        String name,
        CouponType type,
        int value,
        int minOrderAmount,
        LocalDateTime expiredAt,
        CouponStatus status
) {
}
