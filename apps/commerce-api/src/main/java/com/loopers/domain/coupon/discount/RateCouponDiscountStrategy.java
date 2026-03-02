package com.loopers.domain.coupon.discount;

import org.springframework.stereotype.Component;

import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.shared.Money;

@Component
public class RateCouponDiscountStrategy implements CouponDiscountStrategy {

    @Override
    public CouponType getType() {
        return CouponType.RATE;
    }

    @Override
    public Money calculate(Long discountValue, Money orderTotal, Money maxDiscountPrice) {
        Money calculated = Money.wons(orderTotal.getAmount() * discountValue / 100);
        return Money.min(calculated, maxDiscountPrice);
    }
}
