package com.loopers.domain.coupon;

import com.loopers.domain.product.Money;

public enum CouponType {
    FIXED {
        @Override
        public Money calculateDiscount(int value, Money orderAmount) {
            return new Money(value).min(orderAmount);
        }
    },
    RATE {
        @Override
        public Money calculateDiscount(int value, Money orderAmount) {
            return orderAmount.multiply(value).divide(100).min(orderAmount);
        }
    };

    public abstract Money calculateDiscount(int value, Money orderAmount);
}
