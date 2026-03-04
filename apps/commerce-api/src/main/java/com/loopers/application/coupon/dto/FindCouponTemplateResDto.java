package com.loopers.application.coupon.dto;

import com.loopers.domain.coupon.model.CouponTemplate;

import java.time.LocalDateTime;

public record FindCouponTemplateResDto(
        Long id,
        String name,
        String type,
        int discountValue,
        int minOrderAmount,
        LocalDateTime expiredAt
) {
    public static FindCouponTemplateResDto from(CouponTemplate template) {
        return new FindCouponTemplateResDto(
                template.getId(),
                template.getName().value(),
                template.getType().name(),
                template.getDiscountValue().value(),
                template.getMinOrderAmount().value(),
                template.getExpiredAt()
        );
    }
}
