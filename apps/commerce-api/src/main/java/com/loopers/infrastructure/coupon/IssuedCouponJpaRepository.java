package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssuedCouponJpaRepository extends JpaRepository<IssuedCoupon, Long> {
    long countByCouponTemplateId(Long couponTemplateId);
    long countByCouponTemplateIdAndUserId(Long couponTemplateId, Long userId);
    List<IssuedCoupon> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
