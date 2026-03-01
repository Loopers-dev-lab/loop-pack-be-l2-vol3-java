package com.loopers.application.coupon;

import java.time.ZonedDateTime;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponType;

public record CouponResult(
        Long id,
        String name,
        CouponType type,
        Long discountValue,
        Long maxDiscountPrice,
        Long minOrderPrice,
        ZonedDateTime expiredAt,
        ZonedDateTime createdAt,
        ZonedDateTime deletedAt
) {

    public static CouponResult from(Coupon coupon) {
        return new CouponResult(
                coupon.getId(),
                coupon.getName().getValue(),
                coupon.getType(),
                coupon.getDiscountValue(),
                coupon.getMaxDiscountPrice() != null ? coupon.getMaxDiscountPrice().getAmount() : null,
                coupon.getMinOrderPrice().getAmount(),
                coupon.getExpiredAt(),
                coupon.getCreatedAt(),
                coupon.getDeletedAt()
        );
    }
}
