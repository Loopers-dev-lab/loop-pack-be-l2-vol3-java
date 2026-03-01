package com.loopers.domain.coupon;

public interface OwnedCouponRepository {

    OwnedCoupon save(OwnedCoupon ownedCoupon);

    boolean existsByCouponIdAndUserId(Long couponId, Long userId);
}
