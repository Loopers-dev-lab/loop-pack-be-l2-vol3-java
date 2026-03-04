package com.loopers.domain.coupon;

import java.util.List;

public interface IssuedCouponRepository {

    // Command

    IssuedCoupon save(IssuedCoupon issuedCoupon);

    // Query

    List<IssuedCoupon> findAllByCouponId(Long couponId);
}
