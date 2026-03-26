package com.loopers.domain.coupon;

import java.util.Optional;

public interface CouponIssueResultRepository {

    CouponIssueResult save(CouponIssueResult result);

    Optional<CouponIssueResult> findByUserIdAndCouponId(Long userId, Long couponId);
}
