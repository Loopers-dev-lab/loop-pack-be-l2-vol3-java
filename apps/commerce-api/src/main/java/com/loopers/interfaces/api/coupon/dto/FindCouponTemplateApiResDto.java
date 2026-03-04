package com.loopers.interfaces.api.coupon.dto;

import com.loopers.application.coupon.dto.FindCouponTemplateResDto;

import java.time.LocalDateTime;

public record FindCouponTemplateApiResDto(
        Long id,
        String name,
        String type,
        int discountValue,
        int minOrderAmount,
        LocalDateTime expiredAt
) {
    public static FindCouponTemplateApiResDto from(FindCouponTemplateResDto dto) {
        return new FindCouponTemplateApiResDto(
                dto.id(),
                dto.name(),
                dto.type(),
                dto.discountValue(),
                dto.minOrderAmount(),
                dto.expiredAt()
        );
    }
}
