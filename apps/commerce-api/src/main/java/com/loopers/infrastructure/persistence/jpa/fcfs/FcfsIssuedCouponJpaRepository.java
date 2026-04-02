package com.loopers.infrastructure.persistence.jpa.fcfs;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 발급된 선착순 쿠폰 JPA Repository.
 */
public interface FcfsIssuedCouponJpaRepository extends JpaRepository<FcfsIssuedCouponJpaEntity, Long> {

    boolean existsByCouponIdAndUserId(Long couponId, Long userId);

    long countByCouponId(Long couponId);
}
