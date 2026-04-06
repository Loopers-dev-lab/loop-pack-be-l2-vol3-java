package com.loopers.infrastructure.coupon.repository;

import com.loopers.infrastructure.coupon.entity.FirstComeCouponEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FirstComeCouponJpaRepository extends JpaRepository<FirstComeCouponEntity, Long> {

    Optional<FirstComeCouponEntity> findByCouponTemplateId(Long couponTemplateId);
}
