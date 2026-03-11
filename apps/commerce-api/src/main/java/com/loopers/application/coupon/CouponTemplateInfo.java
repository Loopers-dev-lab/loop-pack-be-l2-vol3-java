package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.DiscountType;
import java.time.ZonedDateTime;

public record CouponTemplateInfo(
    Long id,
    String name,
    DiscountType discountType,
    Integer discountValue,
    Integer minOrderAmount,
    ZonedDateTime expiredAt
) {
    public static CouponTemplateInfo from(CouponTemplate template) {
        return new CouponTemplateInfo(
            template.getId(),
            template.getName().value(),
            template.getType(),
            template.getDiscountValue(),
            template.getMinOrderAmount().value(),
            template.getExpiredAt()
        );
    }
}
