package com.loopers.domain.coupon.dto;

import com.loopers.domain.coupon.CouponDiscountType;
import java.time.ZonedDateTime;

public class CouponCommand {

    public record Create(
        String name,
        CouponDiscountType discountType,
        long discountValue,
        Long minOrderAmount,
        int totalQuantity,
        ZonedDateTime expiredAt
    ) {}

    public record Update(
        String name,
        ZonedDateTime expiredAt
    ) {}
}
