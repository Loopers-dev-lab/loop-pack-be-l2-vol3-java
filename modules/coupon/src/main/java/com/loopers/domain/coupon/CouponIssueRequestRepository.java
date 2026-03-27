package com.loopers.domain.coupon;

import java.util.Optional;

public interface CouponIssueRequestRepository {

    CouponIssueRequestModel save(CouponIssueRequestModel model);

    Optional<CouponIssueRequestModel> findByRequestId(String requestId);

    Optional<CouponIssueRequestModel> findByRequestIdAndUserId(String requestId, Long userId);
}
