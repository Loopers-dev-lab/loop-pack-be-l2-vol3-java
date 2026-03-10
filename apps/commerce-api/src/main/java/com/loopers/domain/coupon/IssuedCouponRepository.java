package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface IssuedCouponRepository {
    IssuedCoupon save(IssuedCoupon issuedCoupon);
    Optional<IssuedCoupon> findById(Long id);
    Optional<IssuedCoupon> findByIdWithLock(Long id);
    Optional<IssuedCoupon> findByCouponIdAndUserId(Long couponId, Long userId);
    Optional<IssuedCoupon> findByCouponIdAndUserIdWithLock(Long couponId, Long userId);
    List<IssuedCoupon> findByUserId(Long userId);
    List<IssuedCoupon> findByCouponId(Long couponId);
    Page<IssuedCoupon> findByCouponId(Long couponId, Pageable pageable);
}
