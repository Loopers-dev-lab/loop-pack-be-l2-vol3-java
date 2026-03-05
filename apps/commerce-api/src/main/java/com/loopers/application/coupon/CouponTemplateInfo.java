package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponType;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

public record CouponTemplateInfo(
        Long id,
        String name,
        CouponType type,
        int value,
        Integer minOrderAmount,
        LocalDateTime expiredAt,
        ZonedDateTime createdAt
) {
    public static CouponTemplateInfo from(CouponTemplate template) {
        return new CouponTemplateInfo(
                template.getId(),
                template.getName(),
                template.getType(),
                template.getValue(),
                template.getMinOrderAmount(),
                template.getExpiredAt(),
                template.getCreatedAt()
        );
    }
}
