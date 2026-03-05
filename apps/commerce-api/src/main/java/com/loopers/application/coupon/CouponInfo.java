package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponType;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record CouponInfo(
        Long id,
        String name,
        CouponType type,
        BigDecimal value,
        BigDecimal minOrderAmount,
        ZonedDateTime expiredAt,
        boolean expired,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {
    public static CouponInfo from(Coupon coupon) {
        return new CouponInfo(
                coupon.getId(),
                coupon.getName(),
                coupon.getType(),
                coupon.getValue(),
                coupon.getMinOrderAmount(),
                coupon.getExpiredAt(),
                coupon.isExpired(),
                coupon.getCreatedAt(),
                coupon.getUpdatedAt()
        );
    }
}
