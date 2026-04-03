package com.loopers.infrastructure.coupon;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IssuedCouponJpaRepository extends JpaRepository<IssuedCouponEntity, UUID> {
}
