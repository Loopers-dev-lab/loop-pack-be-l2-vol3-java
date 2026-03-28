package com.loopers.domain.coupon;

import java.util.Optional;

public interface FcfsCouponRepository {

    FcfsCoupon save(FcfsCoupon fcfsCoupon);

    Optional<FcfsCoupon> findByCouponId(Long couponId);
}
