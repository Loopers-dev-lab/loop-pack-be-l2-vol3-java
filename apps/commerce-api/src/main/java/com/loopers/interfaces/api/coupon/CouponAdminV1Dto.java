package com.loopers.interfaces.api.coupon;

import com.loopers.domain.coupon.CouponType;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public class CouponAdminV1Dto {

    public record RegisterRequest(
            String name,
            CouponType type,
            BigDecimal value,
            BigDecimal minOrderAmount,
            ZonedDateTime expiredAt,
            int totalQuantity
    ) {}

    public record ModifyRequest(
            String name,
            BigDecimal value,
            BigDecimal minOrderAmount,
            ZonedDateTime expiredAt
    ) {}

    public record CouponResponse(
            Long id,
            String name,
            CouponType type,
            BigDecimal value,
            BigDecimal minOrderAmount,
            ZonedDateTime expiredAt
    ) {}

    public record UserCouponResponse(
            Long userCouponId,
            String couponName,
            CouponType type,
            BigDecimal value,
            BigDecimal minOrderAmount,
            String status,
            ZonedDateTime expiredAt
    ) {}
}
