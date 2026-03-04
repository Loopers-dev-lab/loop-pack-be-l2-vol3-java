package com.loopers.interfaces.api.coupon.dto;

import com.loopers.application.coupon.dto.IssueCouponResDto;

public record IssueCouponApiResDto(
        Long userCouponId,
        Long couponTemplateId,
        String status
) {
    public static IssueCouponApiResDto from(IssueCouponResDto dto) {
        return new IssueCouponApiResDto(
                dto.userCouponId(),
                dto.couponTemplateId(),
                dto.status()
        );
    }
}
