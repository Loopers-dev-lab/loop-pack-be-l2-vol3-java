package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface IssuedCouponRepository {

    // Command

    IssuedCoupon save(IssuedCoupon issuedCoupon);

    // Query

    Optional<IssuedCoupon> findByIdForUpdate(Long id);

    List<IssuedCoupon> findAllByCouponId(Long couponId);

    Page<IssuedCoupon> findAllByCouponId(Long couponId, Pageable pageable);

    Page<IssuedCoupon> findActiveByUserId(Long userId, Pageable pageable);

    boolean existsByCouponIdAndUserId(Long couponId, Long userId);
}
