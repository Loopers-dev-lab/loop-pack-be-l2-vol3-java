package com.loopers.infrastructure.coupon.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.coupon.OwnedCoupon;

public interface OwnedCouponJpaRepository extends JpaRepository<OwnedCoupon, Long> {

    boolean existsByCouponIdAndUserId(Long couponId, Long userId);
}
