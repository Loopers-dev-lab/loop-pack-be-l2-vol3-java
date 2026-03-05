package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.UserCoupon;

import java.time.ZonedDateTime;

public record UserCouponInfo(
        Long id,
        Long userId,
        Long couponId,
        CouponStatus status,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {
    public static UserCouponInfo from(UserCoupon userCoupon, Coupon coupon) {
        CouponStatus effectiveStatus = userCoupon.getStatus();
        if (effectiveStatus == CouponStatus.AVAILABLE && coupon.isExpired()) {
            effectiveStatus = CouponStatus.EXPIRED;
        }
        return new UserCouponInfo(
                userCoupon.getId(),
                userCoupon.getUserId(),
                userCoupon.getCouponId(),
                effectiveStatus,
                userCoupon.getCreatedAt(),
                userCoupon.getUpdatedAt()
        );
    }

    public static UserCouponInfo from(UserCoupon userCoupon) {
        return new UserCouponInfo(
                userCoupon.getId(),
                userCoupon.getUserId(),
                userCoupon.getCouponId(),
                userCoupon.getStatus(),
                userCoupon.getCreatedAt(),
                userCoupon.getUpdatedAt()
        );
    }
}
