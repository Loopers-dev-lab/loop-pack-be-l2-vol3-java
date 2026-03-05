package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record IssuedCouponInfo(
        Long id,
        Long couponId,
        String couponName,
        CouponType type,
        int value,
        BigDecimal minOrderAmount,
        String status,
        LocalDateTime expiredAt,
        LocalDateTime createdAt,
        LocalDateTime usedAt
) {
    public static IssuedCouponInfo from(IssuedCoupon issuedCoupon) {
        String status;
        if (issuedCoupon.isUsed()) {
            status = "USED";
        } else if (issuedCoupon.isExpired()) {
            status = "EXPIRED";
        } else {
            status = "AVAILABLE";
        }
        return new IssuedCouponInfo(
                issuedCoupon.getId(),
                issuedCoupon.getCouponId(),
                issuedCoupon.getCouponName(),
                issuedCoupon.getCouponType(),
                issuedCoupon.getCouponValue(),
                issuedCoupon.getMinOrderAmount(),
                status,
                issuedCoupon.getExpiredAt(),
                issuedCoupon.getCreatedAt().toLocalDateTime(),
                issuedCoupon.getUsedAt()
        );
    }
}
