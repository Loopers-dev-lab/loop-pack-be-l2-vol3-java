package com.loopers.domain.coupon;

import com.loopers.domain.coupon.dto.CouponCommand;
import java.time.ZonedDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SignupCouponPolicy {

    WELCOME("웰컴 쿠폰", CouponDiscountType.FIXED, 5000L, null, 1, 30);

    private final String name;
    private final CouponDiscountType discountType;
    private final long discountValue;
    private final Long minOrderAmount;
    private final int totalQuantity;
    private final int validDays;

    public CouponCommand.Create toCreateCommand() {
        return new CouponCommand.Create(
                name, discountType, discountValue, minOrderAmount,
                totalQuantity, ZonedDateTime.now().plusDays(validDays));
    }
}
