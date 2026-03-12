package com.loopers.domain.coupon;

import com.loopers.domain.common.Money;

/**
 * 정률 할인 정책.
 */
public class RateCouponDiscountPolicy implements CouponDiscountPolicy {

    @Override
    public Money calculateDiscount(Money orderAmount, Money rateValue, Integer maxDiscountAmount) {
        // rateValue는 퍼센트 * 100 (예: 10% = 1000)
        long discount = orderAmount.amount() * rateValue.amount() / 10000;
        if (maxDiscountAmount != null && discount > maxDiscountAmount) {
            discount = maxDiscountAmount;
        }
        return new Money(discount);
    }
}
