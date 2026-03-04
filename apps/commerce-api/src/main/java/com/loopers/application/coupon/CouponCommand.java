package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponCommand() {

    public record Register(String name, CouponType type, int value, BigDecimal minOrderAmount,
                           int maxIssueCount, LocalDateTime expiredAt) {
        public static Register of(String name, CouponType type, int value, BigDecimal minOrderAmount,
                                  int maxIssueCount, LocalDateTime expiredAt) {
            return new Register(name, type, value, minOrderAmount, maxIssueCount, expiredAt);
        }
    }
}
