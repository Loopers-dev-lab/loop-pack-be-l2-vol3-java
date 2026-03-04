package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;

import java.time.LocalDateTime;

public record IssuedCouponInfo(
        Long id,
        Long couponId,
        String couponName,
        CouponType type,
        int value,
        String status,
        LocalDateTime expiredAt,
        LocalDateTime createdAt
) {
    public static IssuedCouponInfo from(IssuedCoupon issuedCoupon, Coupon coupon) {
        String status = issuedCoupon.isUsed() ? "USED" : "AVAILABLE";
        return new IssuedCouponInfo(
                issuedCoupon.getId(),
                coupon.getId(),
                coupon.getName(),
                coupon.getType(),
                coupon.getValue(),
                status,
                coupon.getExpiredAt(),
                issuedCoupon.getCreatedAt().toLocalDateTime()
        );
    }
}
