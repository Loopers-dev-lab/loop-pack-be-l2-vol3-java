package com.loopers.domain.coupon;

public enum CouponUsageType {

    AVAILABLE("사용 가능"),
    USED("사용 완료"),
    EXPIRED("만료");

    private final String desc;

    CouponUsageType(String desc) {
        this.desc = desc;
    }
}
