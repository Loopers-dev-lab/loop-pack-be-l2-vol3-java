package com.loopers.domain.coupon;

public interface CouponTemplateRepository {
    long decreaseStockIfAvailable(Long couponTemplateId);
}
