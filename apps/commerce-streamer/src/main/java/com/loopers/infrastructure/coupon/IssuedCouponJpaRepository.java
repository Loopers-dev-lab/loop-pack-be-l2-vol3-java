package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssuedCouponJpaRepository extends JpaRepository<IssuedCoupon, Long> {

    boolean existsByCouponIdAndUserId(Long couponId, Long userId);

    long countByCouponId(Long couponId);
}
