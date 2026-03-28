package com.loopers.domain.coupon;

import java.util.Optional;

public interface FcfsCouponRepository {

    Optional<FcfsCoupon> findByCouponId(Long couponId);

    FcfsCoupon save(FcfsCoupon fcfsCoupon);
}
