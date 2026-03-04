package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponInfo(
        Long id,
        String name,
        CouponType type,
        int value,
        BigDecimal minOrderAmount,
        int maxIssueCount,
        int issuedCount,
        LocalDateTime expiredAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CouponInfo from(Coupon coupon) {
        return new CouponInfo(
                coupon.getId(),
                coupon.getName(),
                coupon.getType(),
                coupon.getValue(),
                coupon.getMinOrderAmount(),
                coupon.getMaxIssueCount(),
                coupon.getIssuedCount(),
                coupon.getExpiredAt(),
                coupon.getCreatedAt().toLocalDateTime(),
                coupon.getUpdatedAt().toLocalDateTime()
        );
    }
}
