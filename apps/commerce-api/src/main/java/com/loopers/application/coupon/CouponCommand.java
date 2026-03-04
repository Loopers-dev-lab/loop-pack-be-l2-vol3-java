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

    public record Update(CouponType type, String name, Integer value, BigDecimal minOrderAmount,
                         Integer maxIssueCount, LocalDateTime expiredAt) {
        public static Update of(CouponType type, String name, Integer value, BigDecimal minOrderAmount,
                                Integer maxIssueCount, LocalDateTime expiredAt) {
            return new Update(type, name, value, minOrderAmount, maxIssueCount, expiredAt);
        }
    }
}
