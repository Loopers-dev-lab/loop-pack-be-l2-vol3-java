package com.loopers.domain.coupon.model;

import com.loopers.support.CouponEnums;

import java.time.LocalDateTime;

public final class CouponCommand {

    private CouponCommand() {}

    public record CreateTemplate(
            String name,
            CouponEnums.Type type,
            int discountValue,
            int minOrderAmount,
            LocalDateTime expiredAt
    ) {}

    public record UpdateTemplate(
            String name,
            CouponEnums.Type type,
            int discountValue,
            int minOrderAmount,
            LocalDateTime expiredAt
    ) {}
}
