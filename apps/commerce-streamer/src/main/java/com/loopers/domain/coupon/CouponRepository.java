package com.loopers.domain.coupon;

public interface CouponRepository {

    int issueIfAvailable(Long couponId);
}
