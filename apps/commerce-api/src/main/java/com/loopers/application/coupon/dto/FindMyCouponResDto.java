package com.loopers.application.coupon.dto;

import com.loopers.domain.coupon.model.UserCouponItem;

import java.time.LocalDateTime;

public record FindMyCouponResDto(
        Long userCouponId,
        String couponName,
        String type,
        int discountValue,
        int minOrderAmount,
        String status,
        LocalDateTime expiredAt,
        LocalDateTime usedAt
) {
    public static FindMyCouponResDto from(UserCouponItem item) {
        return new FindMyCouponResDto(
                item.userCouponId(),
                item.couponName(),
                item.type().name(),
                item.discountValue(),
                item.minOrderAmount(),
                item.status().name(),
                item.expiredAt(),
                item.usedAt()
        );
    }
}
