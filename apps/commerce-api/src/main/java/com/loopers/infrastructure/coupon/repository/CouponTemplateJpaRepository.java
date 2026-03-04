package com.loopers.infrastructure.coupon.repository;

import com.loopers.infrastructure.coupon.entity.CouponTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponTemplateJpaRepository extends JpaRepository<CouponTemplateEntity, Long> {
}
