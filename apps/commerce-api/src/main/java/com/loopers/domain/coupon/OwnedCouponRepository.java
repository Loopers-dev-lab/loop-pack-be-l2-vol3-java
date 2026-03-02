package com.loopers.domain.coupon;

import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface OwnedCouponRepository {

    OwnedCoupon save(OwnedCoupon ownedCoupon);

    Optional<OwnedCoupon> findByIdWithCoupon(Long id);

    Slice<OwnedCoupon> findAllByCouponId(Long couponId, Pageable pageable);

    Slice<OwnedCoupon> findAllByUserId(Long userId, Pageable pageable);

    boolean existsByCouponIdAndUserId(Long couponId, Long userId);
}
