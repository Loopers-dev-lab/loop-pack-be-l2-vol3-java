package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponIssueJpaRepository extends JpaRepository<CouponIssueEntity, Long> {

    Optional<CouponIssueEntity> findByUserIdAndCouponId(Long userId, Long couponId);
}
