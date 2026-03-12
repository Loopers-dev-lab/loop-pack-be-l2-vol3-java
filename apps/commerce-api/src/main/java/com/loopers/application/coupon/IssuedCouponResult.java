package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponDisplayStatus;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponStatus;

import java.time.ZonedDateTime;

/**
 * 발급된 쿠폰 조회 결과.
 */
public record IssuedCouponResult(
    Long id,
    Long userId,
    Long couponTemplateId,
    String couponName,
    CouponType couponType,
    long couponValue,
    long minOrderAmount,
    Integer maxDiscountAmount,
    IssuedCouponStatus status,
    CouponDisplayStatus displayStatus,
    boolean usable,
    ZonedDateTime usedAt,
    ZonedDateTime issuedAt,
    ZonedDateTime expiredAt
) {
    public static IssuedCouponResult from(IssuedCoupon coupon, CouponTemplate template) {
        return new IssuedCouponResult(
            coupon.getId(),
            coupon.getUserId(),
            coupon.getCouponTemplateId(),
            template.getName(),
            template.getType(),
            template.getValue().amount(),
            template.getMinOrderAmount().amount(),
            template.getMaxDiscountAmount(),
            coupon.getStatus(),
            coupon.getDisplayStatus(),
            coupon.isUsable(),
            coupon.getUsedAt(),
            coupon.getIssuedAt(),
            coupon.getExpiredAt()
        );
    }

    public static IssuedCouponResult simple(IssuedCoupon coupon) {
        return new IssuedCouponResult(
            coupon.getId(),
            coupon.getUserId(),
            coupon.getCouponTemplateId(),
            null,
            null,
            0,
            0,
            null,
            coupon.getStatus(),
            coupon.getDisplayStatus(),
            coupon.isUsable(),
            coupon.getUsedAt(),
            coupon.getIssuedAt(),
            coupon.getExpiredAt()
        );
    }
}
