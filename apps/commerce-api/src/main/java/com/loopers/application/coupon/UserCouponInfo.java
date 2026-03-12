package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.UserCoupon;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

public record UserCouponInfo(
        Long id,
        Long couponTemplateId,
        Long userId,
        CouponStatus status,
        LocalDateTime expiredAt,
        ZonedDateTime issuedAt,
        LocalDateTime usedAt
) {
    public static UserCouponInfo from(UserCoupon userCoupon, LocalDateTime now) {
        LocalDateTime expiredAt = userCoupon.getExpiredAt();
        CouponStatus status = resolveStatus(userCoupon, expiredAt, now);
        return new UserCouponInfo(
                userCoupon.getId(),
                userCoupon.getCouponTemplateId(),
                userCoupon.getUserId(),
                status,
                expiredAt,
                userCoupon.getCreatedAt(),
                userCoupon.getUsedAt()
        );
    }

    // 쿠폰 상태 반환
    private static CouponStatus resolveStatus(UserCoupon userCoupon, LocalDateTime expiredAt, LocalDateTime now) {
        if (userCoupon.getUsedAt() != null) {
            return CouponStatus.USED;
        }
        if (now.isAfter(expiredAt)) {
            return CouponStatus.EXPIRED;
        }
        return CouponStatus.AVAILABLE;
    }
}
