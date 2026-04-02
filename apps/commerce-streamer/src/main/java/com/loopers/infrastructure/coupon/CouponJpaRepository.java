package com.loopers.infrastructure.coupon;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponJpaRepository extends JpaRepository<CouponEntity, Long> {

    boolean existsByRefUserIdAndRefTemplateId(Long refUserId, Long refTemplateId);
}
