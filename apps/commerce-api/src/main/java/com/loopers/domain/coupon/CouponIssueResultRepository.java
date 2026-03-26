package com.loopers.domain.coupon;

import java.util.Optional;

public interface CouponIssueResultRepository {
    CouponIssueResultModel save(CouponIssueResultModel model);
    Optional<CouponIssueResultModel> findByRequestId(String requestId);
}
