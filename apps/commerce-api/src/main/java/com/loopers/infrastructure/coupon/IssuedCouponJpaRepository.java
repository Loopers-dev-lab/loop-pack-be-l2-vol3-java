package com.loopers.infrastructure.coupon;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssuedCouponJpaRepository extends JpaRepository<IssuedCouponEntity, Long> {
    long countByCouponTemplateId(Long couponTemplateId);
    long countByCouponTemplateIdAndUserId(Long couponTemplateId, Long userId);
    List<IssuedCouponEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
