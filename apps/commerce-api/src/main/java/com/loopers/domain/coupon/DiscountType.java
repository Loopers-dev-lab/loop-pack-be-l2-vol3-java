package com.loopers.domain.coupon;

public enum DiscountType {

    FIXED("정액"),
    RATE("정률");

    private final String type;

    DiscountType(String type) {
        this.type = type;
    }
}
