package com.loopers.application.coupon;

import java.time.ZonedDateTime;

import com.loopers.domain.coupon.OwnedCoupon;
import com.loopers.domain.coupon.OwnedCouponStatus;
import com.loopers.domain.user.User;

public record OwnedCouponResult(
        Long id,
        Long couponId,
        Long userId,
        String loginId,
        String userName,
        OwnedCouponStatus status,
        ZonedDateTime createdAt
) {

    public static OwnedCouponResult from(OwnedCoupon ownedCoupon, User user) {
        return new OwnedCouponResult(
                ownedCoupon.getId(),
                ownedCoupon.getCoupon().getId(),
                ownedCoupon.getUserId(),
                user.getLoginId().getValue(),
                user.getName().getValue(),
                ownedCoupon.getStatus(),
                ownedCoupon.getCreatedAt()
        );
    }
}
