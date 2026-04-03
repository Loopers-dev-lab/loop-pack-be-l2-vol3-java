package com.loopers.domain.coupon;

import java.util.Optional;

public interface CouponIssueResultRepository {

    CouponIssueResult save(CouponIssueResult result);

    Optional<CouponIssueResult> findById(Long id);

    Optional<CouponIssueResult> findByIdAndUserId(Long id, Long userId);
}
