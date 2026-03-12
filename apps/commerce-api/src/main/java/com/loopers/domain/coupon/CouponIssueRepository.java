package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface CouponIssueRepository {

    CouponIssueModel save(CouponIssueModel couponIssue);

    Optional<CouponIssueModel> findById(Long id);

    Optional<CouponIssueModel> findByIdForUpdate(Long id);

    List<CouponIssueModel> findAllByUserId(Long userId);

    Page<CouponIssueModel> findAllByCouponId(Long couponId, Pageable pageable);

    Optional<CouponIssueModel> findByUserIdAndCouponId(Long userId, Long couponId);
}
