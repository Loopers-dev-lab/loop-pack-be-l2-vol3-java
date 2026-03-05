package com.loopers.domain.coupon;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface CouponIssueRepository {
    CouponIssue save(CouponIssue couponIssue);
    Optional<CouponIssue> findById(Long id);
    int markAsUsed(Long id, ZonedDateTime now);
    List<CouponIssue> findAllByMemberId(Long memberId);
    List<CouponIssue> findAllByCouponId(Long couponId);
}
