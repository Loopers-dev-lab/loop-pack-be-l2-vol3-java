package com.loopers.domain.coupon;

import java.util.List;
import java.util.Optional;

public interface CouponIssueRepository {
    CouponIssue save(CouponIssue couponIssue);
    Optional<CouponIssue> findById(Long id);
    Optional<CouponIssue> findByIdWithLock(Long id);
    List<CouponIssue> findAllByMemberId(Long memberId);
    List<CouponIssue> findAllByCouponId(Long couponId);
}
