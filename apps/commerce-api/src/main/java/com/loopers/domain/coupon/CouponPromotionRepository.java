package com.loopers.domain.coupon;

import java.util.Optional;

public interface CouponPromotionRepository {
    Optional<CouponPromotion> findByCouponId(Long couponId);
}
