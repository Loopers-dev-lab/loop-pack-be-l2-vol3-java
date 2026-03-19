package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface IssuedCouponRepository {
    IssuedCoupon save(IssuedCoupon issuedCoupon);
    Optional<IssuedCoupon> findByIdAndUserId(Long id, Long userId);
    List<IssuedCoupon> findByUserId(Long userId);
    boolean existsByUserIdAndCouponId(Long userId, Long couponId);
    int useById(Long id, Long userId);
    int restoreById(Long id, Long userId);
    Page<IssuedCoupon> findByCouponId(Long couponId, Pageable pageable);
}
