package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record IssuedCouponCommand() {

    public record Issue(
            Long couponId,
            Long userId,
            String couponName,
            CouponType couponType,
            int couponValue,
            BigDecimal minOrderAmount,
            LocalDateTime expiredAt
    ) {
        public static Issue of(Long couponId, Long userId, String couponName,
                               CouponType couponType, int couponValue,
                               BigDecimal minOrderAmount, LocalDateTime expiredAt) {
            return new Issue(couponId, userId, couponName, couponType, couponValue,
                    minOrderAmount, expiredAt);
        }

        public static Issue from(Coupon coupon, Long userId) {
            return new Issue(coupon.getId(), userId, coupon.getName(), coupon.getType(),
                    coupon.getValue(), coupon.getMinOrderAmount(), coupon.getExpiredAt());
        }
    }
}
