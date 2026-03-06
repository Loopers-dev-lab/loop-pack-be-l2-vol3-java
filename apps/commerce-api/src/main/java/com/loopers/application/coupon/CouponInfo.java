package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.UserCoupon;

import java.time.ZonedDateTime;

public record CouponInfo(
    Long id,
    Long userId,
    Long couponTemplateId,
    CouponStatus status,
    ZonedDateTime issuedAt,
    ZonedDateTime expiredAt,
    ZonedDateTime usedAt
) {
    public static CouponInfo from(UserCoupon userCoupon) {
        return new CouponInfo(
            userCoupon.getId(),
            userCoupon.getUserId(),
            userCoupon.getCouponTemplateId(),
            userCoupon.getStatus(),
            userCoupon.getIssuedAt(),
            userCoupon.getExpiredAt(),
            userCoupon.getUsedAt()
        );
    }
}
