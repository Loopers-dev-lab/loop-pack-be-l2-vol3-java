package com.loopers.domain.coupon.model;

import com.loopers.support.CouponEnums;

import java.time.LocalDateTime;

public record UserCouponItem(
    Long userCouponId,
    Long couponTemplateId,
    String couponName,
    CouponEnums.Type type,
    int discountValue,
    int minOrderAmount,
    CouponEnums.Status status,
    LocalDateTime expiredAt,
    LocalDateTime usedAt
) {}
