package com.loopers.domain.coupon.discount;

import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.shared.Money;

public interface CouponDiscountStrategy {

    CouponType getType();

    Money calculate(Long discountValue, Money orderTotal, Money maxDiscountPrice);
}
