package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface CouponIssueRepository {

    CouponIssue save(CouponIssue couponIssue);

    Optional<CouponIssue> findById(Long id);

    Optional<CouponIssue> findByIdAndUserId(Long id, Long userId);

    Optional<CouponIssue> findByCouponIdAndUserId(Long couponId, Long userId);

    Page<CouponIssue> findByCouponId(Long couponId, Pageable pageable);

    Page<CouponIssue> findByUserId(Long userId, Pageable pageable);
}
