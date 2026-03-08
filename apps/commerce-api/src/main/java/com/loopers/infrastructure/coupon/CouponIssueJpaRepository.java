package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponIssueJpaRepository extends JpaRepository<CouponIssue, Long> {

    Optional<CouponIssue> findByIdAndUserId(Long id, Long userId);

    Optional<CouponIssue> findByCouponIdAndUserId(Long couponId, Long userId);

    Page<CouponIssue> findAllByCouponId(Long couponId, Pageable pageable);

    Page<CouponIssue> findAllByUserId(Long userId, Pageable pageable);
}
