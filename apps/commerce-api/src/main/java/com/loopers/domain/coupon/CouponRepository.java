package com.loopers.domain.coupon;

import java.util.Optional;

public interface CouponRepository {

    // Command

    Coupon save(Coupon coupon);

    // Query

    Optional<Coupon> findById(Long id);
}
