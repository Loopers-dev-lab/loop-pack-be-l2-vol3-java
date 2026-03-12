package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

/**
 * 쿠폰 템플릿 조회 결과.
 */
public record CouponTemplateResult(
    Long id,
    String name,
    CouponType type,
    long value,
    long minOrderAmount,
    Integer maxDiscountAmount,
    Integer maxIssueCount,
    int issuedCount,
    ZonedDateTime expiredAt,
    ZonedDateTime createdAt
) {
    public static CouponTemplateResult from(CouponTemplate template) {
        return new CouponTemplateResult(
            template.getId(),
            template.getName(),
            template.getType(),
            template.getValue().amount(),
            template.getMinOrderAmount().amount(),
            template.getMaxDiscountAmount(),
            template.getMaxIssueCount(),
            template.getIssuedCount(),
            template.getExpiredAt(),
            template.getCreatedAt()
        );
    }
}
