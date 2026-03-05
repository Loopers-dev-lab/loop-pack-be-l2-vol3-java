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

    public record UpdateInfo(CouponType type, String name, Integer value, BigDecimal minOrderAmount,
                             Integer maxIssueCount, LocalDateTime expiredAt) {
        public static UpdateInfo of(CouponType type, String name, Integer value, BigDecimal minOrderAmount,
                                    Integer maxIssueCount, LocalDateTime expiredAt) {
            return new UpdateInfo(type, name, value, minOrderAmount, maxIssueCount, expiredAt);
        }
    }
}
