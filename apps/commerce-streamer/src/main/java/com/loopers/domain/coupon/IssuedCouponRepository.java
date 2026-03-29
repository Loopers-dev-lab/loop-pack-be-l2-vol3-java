package com.loopers.domain.coupon;

import java.util.Optional;

public interface IssuedCouponRepository {
    IssuedCoupon save(IssuedCoupon issuedCoupon);
    Optional<IssuedCoupon> findByCouponIdAndUserId(Long couponId, Long userId);
}
