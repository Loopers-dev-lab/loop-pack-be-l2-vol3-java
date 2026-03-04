package com.loopers.interfaces.api.coupon.dto;

import com.loopers.application.coupon.dto.FindMyCouponResDto;

import java.time.LocalDateTime;

public record FindMyCouponApiResDto(
        Long userCouponId,
        String couponName,
        String type,
        int discountValue,
        int minOrderAmount,
        String status,
        LocalDateTime expiredAt,
        LocalDateTime usedAt
) {
    public static FindMyCouponApiResDto from(FindMyCouponResDto dto) {
        return new FindMyCouponApiResDto(
                dto.userCouponId(),
                dto.couponName(),
                dto.type(),
                dto.discountValue(),
                dto.minOrderAmount(),
                dto.status(),
                dto.expiredAt(),
                dto.usedAt()
        );
    }
}
