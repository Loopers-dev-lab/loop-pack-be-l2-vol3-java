package com.loopers.infrastructure.coupon.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.coupon.OwnedCoupon;

public interface OwnedCouponJpaRepository extends JpaRepository<OwnedCoupon, Long> {

    boolean existsByCouponIdAndUserId(Long couponId, Long userId);

    Slice<OwnedCoupon> findAllByCouponId(Long couponId, Pageable pageable);
}
