package com.loopers.application.coupon.dto;

import com.loopers.domain.coupon.CouponDiscountType;
import com.loopers.domain.coupon.dto.CouponCommand;
import java.time.ZonedDateTime;

public class CouponCriteria {

    public record Create(
        String name,
        CouponDiscountType discountType,
        long discountValue,
        Long minOrderAmount,
        int totalQuantity,
        ZonedDateTime expiredAt
    ) {
        public CouponCommand.Create toCommand() {
            return new CouponCommand.Create(
                    name, discountType, discountValue,
                    minOrderAmount, totalQuantity, expiredAt);
        }
    }

    public record Update(
        String name,
        ZonedDateTime expiredAt
    ) {
        public CouponCommand.Update toCommand() {
            return new CouponCommand.Update(name, expiredAt);
        }
    }
}
