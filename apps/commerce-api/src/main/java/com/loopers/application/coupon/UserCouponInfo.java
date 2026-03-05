package com.loopers.application.coupon;

import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponStatus;

import java.time.ZonedDateTime;

public record UserCouponInfo(
    Long id,
    Long userId,
    UserCouponStatus status,
    Long orderId,
    ZonedDateTime usedAt,
    CouponInfo coupon,
    ZonedDateTime issuedAt
) {
    public static UserCouponInfo from(UserCouponModel model) {
        return new UserCouponInfo(
            model.getId(),
            model.getUserId(),
            model.getDisplayStatus(),
            model.getOrderId(),
            model.getUsedAt(),
            CouponInfo.from(model.getCoupon()),
            model.getCreatedAt()
        );
    }
}
