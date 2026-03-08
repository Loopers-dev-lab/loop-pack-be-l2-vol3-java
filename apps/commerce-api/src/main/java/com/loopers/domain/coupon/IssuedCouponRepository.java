package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface IssuedCouponRepository {

    // Command

    IssuedCoupon save(IssuedCoupon issuedCoupon);
    int markUsedIfAvailable(Long id, Long userId);

    // Query

    Optional<IssuedCoupon> findById(Long id);

    Page<IssuedCoupon> findAllByCouponId(Long couponId, Pageable pageable);

    Page<IssuedCoupon> findAllByUserId(Long userId, Pageable pageable);

    boolean existsByCouponIdAndUserId(Long couponId, Long userId);
}
