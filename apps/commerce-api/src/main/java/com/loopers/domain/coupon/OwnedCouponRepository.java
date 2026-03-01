package com.loopers.domain.coupon;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface OwnedCouponRepository {

    OwnedCoupon save(OwnedCoupon ownedCoupon);

    Slice<OwnedCoupon> findAllByCouponId(Long couponId, Pageable pageable);

    Slice<OwnedCoupon> findAllByUserId(Long userId, Pageable pageable);

    boolean existsByCouponIdAndUserId(Long couponId, Long userId);
}
