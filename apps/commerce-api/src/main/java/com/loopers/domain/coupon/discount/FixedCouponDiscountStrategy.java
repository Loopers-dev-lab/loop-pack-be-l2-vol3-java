package com.loopers.domain.coupon.discount;

import org.springframework.stereotype.Component;

import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.shared.Money;

@Component
public class FixedCouponDiscountStrategy implements CouponDiscountStrategy {

    @Override
    public CouponType getType() {
        return CouponType.FIXED;
    }

    @Override
    public Money calculate(Long discountValue, Money orderTotal, Money maxDiscountPrice) {
        return Money.min(Money.wons(discountValue), orderTotal);
    }
}
