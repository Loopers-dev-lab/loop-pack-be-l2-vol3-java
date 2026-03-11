package com.loopers.domain.coupon;

import java.util.List;
import java.util.Optional;

public interface CouponRepository {

    Coupon save(Coupon coupon);

    Coupon update(Coupon coupon);

    Optional<Coupon> findById(Long id);

    List<Coupon> findByUserId(Long userId);
}
