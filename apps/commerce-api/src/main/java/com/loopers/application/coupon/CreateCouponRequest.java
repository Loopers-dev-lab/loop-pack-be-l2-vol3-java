package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponType;

import java.time.ZonedDateTime;

/**
 * 쿠폰 생성 요청.
 */
public record CreateCouponRequest(
    String name,
    CouponType type,
    Long value,
    Long minOrderAmount,
    Integer maxDiscountAmount,
    Integer maxIssueCount,
    ZonedDateTime expiredAt
) {}
