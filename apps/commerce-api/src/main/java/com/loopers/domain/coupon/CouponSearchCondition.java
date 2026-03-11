package com.loopers.domain.coupon;

public record CouponSearchCondition(
    int page,
    int size
) {
    public static CouponSearchCondition of(int page, int size) {
        return new CouponSearchCondition(page, size);
    }
}
