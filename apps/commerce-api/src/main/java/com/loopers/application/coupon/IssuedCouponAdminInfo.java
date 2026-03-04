package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.user.User;

import java.time.LocalDateTime;

public record IssuedCouponAdminInfo(
        Long id,
        Long userId,
        String loginId,
        String status,
        LocalDateTime createdAt,
        LocalDateTime usedAt
) {
    public static IssuedCouponAdminInfo from(IssuedCoupon issuedCoupon, User user, Coupon coupon) {
        String status;
        if (issuedCoupon.isUsed()) {
            status = "USED";
        } else if (coupon.isExpired()) {
            status = "EXPIRED";
        } else {
            status = "AVAILABLE";
        }
        return new IssuedCouponAdminInfo(
                issuedCoupon.getId(),
                user.getId(),
                user.getLoginId(),
                status,
                issuedCoupon.getCreatedAt().toLocalDateTime(),
                issuedCoupon.getUsedAt()
        );
    }
}
