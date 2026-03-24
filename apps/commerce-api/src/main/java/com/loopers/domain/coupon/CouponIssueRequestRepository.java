package com.loopers.domain.coupon;

import java.util.Optional;

public interface CouponIssueRequestRepository {

    CouponIssueRequest save(CouponIssueRequest request);

    Optional<CouponIssueRequest> findByRequestIdAndUserId(String requestId, Long userId);

    Optional<CouponIssueRequest> findByCouponIdAndUserId(Long couponId, Long userId);

    Optional<CouponIssueRequest> findByRequestId(String requestId);
}
