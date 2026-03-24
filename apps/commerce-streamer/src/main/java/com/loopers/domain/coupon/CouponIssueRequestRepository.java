package com.loopers.domain.coupon;

import java.util.Optional;

public interface CouponIssueRequestRepository {
    Optional<CouponIssueRequestModel> findByRequestId(String requestId);
    CouponIssueRequestModel save(CouponIssueRequestModel model);
}
