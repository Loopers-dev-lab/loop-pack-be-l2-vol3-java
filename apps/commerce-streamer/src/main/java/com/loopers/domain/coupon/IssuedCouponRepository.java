package com.loopers.domain.coupon;

public interface IssuedCouponRepository {
    boolean existsByUserIdAndCouponId(Long userId, Long couponId);
    IssuedCoupon save(IssuedCoupon issuedCoupon);
}
