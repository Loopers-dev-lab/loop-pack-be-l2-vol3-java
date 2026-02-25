package com.loopers.infrastructure.coupon;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;

public interface CouponTemplateJpaRepository extends JpaRepository<CouponTemplateEntity, Long> {
    List<CouponTemplateEntity> findAllByIdIn(Set<Long> ids);
}
