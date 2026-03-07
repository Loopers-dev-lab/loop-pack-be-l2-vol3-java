package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.user.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record IssuedCouponInfo(
        Long id,
        Long couponId,
        String couponName,
        CouponType type,
        int value,
        BigDecimal minOrderAmount,
        Long userId,
        String loginId,
        Status status,
        LocalDateTime expiredAt,
        LocalDateTime createdAt,
        LocalDateTime usedAt
) {

    public enum Status {
        USED, EXPIRED, AVAILABLE
    }

    public static IssuedCouponInfo from(IssuedCoupon issuedCoupon) {
        return from(issuedCoupon, null, null);
    }

    public static IssuedCouponInfo from(IssuedCoupon issuedCoupon, User user) {
        return from(issuedCoupon, user.getId(), user.getLoginId());
    }

    private static IssuedCouponInfo from(IssuedCoupon issuedCoupon, Long userId, String loginId) {
        Status status;
        if (issuedCoupon.isUsed()) {
            status = Status.USED;
        } else if (issuedCoupon.isExpired()) {
            status = Status.EXPIRED;
        } else {
            status = Status.AVAILABLE;
        }
        return new IssuedCouponInfo(
                issuedCoupon.getId(),
                issuedCoupon.getCouponId(),
                issuedCoupon.getCouponName(),
                issuedCoupon.getCouponType(),
                issuedCoupon.getCouponValue(),
                issuedCoupon.getMinOrderAmount(),
                userId,
                loginId,
                status,
                issuedCoupon.getExpiredAt(),
                issuedCoupon.getCreatedAt().toLocalDateTime(),
                issuedCoupon.getUsedAt()
        );
    }
}
