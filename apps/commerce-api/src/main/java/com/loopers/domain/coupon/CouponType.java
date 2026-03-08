package com.loopers.domain.coupon;

public enum CouponType {

    FIXED("정액 할인"),
    RATE("정률 할인");

    private final String description;

    CouponType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
