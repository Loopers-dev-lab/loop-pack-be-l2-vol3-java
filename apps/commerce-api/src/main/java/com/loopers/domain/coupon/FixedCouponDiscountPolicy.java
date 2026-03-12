package com.loopers.domain.coupon;

import com.loopers.domain.common.Money;

/**
 * 정액 할인 정책.
 */
public class FixedCouponDiscountPolicy implements CouponDiscountPolicy {

    @Override
    public Money calculateDiscount(Money orderAmount, Money couponValue, Integer maxDiscountAmount) {
        long discount = Math.min(orderAmount.amount(), couponValue.amount());
        return new Money(discount);
    }
}
