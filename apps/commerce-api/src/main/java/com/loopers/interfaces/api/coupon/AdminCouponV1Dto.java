package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.IssuedCouponInfo;
import com.loopers.domain.coupon.Coupon;

import java.time.LocalDateTime;

public class AdminCouponV1Dto {

    public record CreateRequest(
        String name,
        String discountType,
        Long discountValue,
        Long minOrderAmount,
        LocalDateTime expiresAt
    ) {}

    public record UpdateRequest(
        String name,
        Long discountValue,
        Long minOrderAmount,
        LocalDateTime expiresAt
    ) {}

    public record CouponResponse(Long id, String name, String discountType, Long discountValue, Long minOrderAmount, LocalDateTime expiresAt) {
        public static CouponResponse from(Coupon coupon) {
            return new CouponResponse(
                coupon.getId(),
                coupon.getName(),
                coupon.getDiscountType().name(),
                coupon.getDiscountValue(),
                coupon.getMinOrderAmount(),
                coupon.getExpiresAt()
            );
        }
    }

    public record IssuedCouponResponse(Long id, Long userId, Long couponId, String status) {
        public static IssuedCouponResponse from(IssuedCouponInfo info) {
            return new IssuedCouponResponse(info.id(), info.userId(), info.couponId(), info.status());
        }
    }
}
