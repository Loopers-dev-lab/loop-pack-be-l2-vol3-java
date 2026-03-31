package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponPromotion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponPromotionJpaRepository extends JpaRepository<CouponPromotion, Long> {
    Optional<CouponPromotion> findByCouponId(Long couponId);
}
