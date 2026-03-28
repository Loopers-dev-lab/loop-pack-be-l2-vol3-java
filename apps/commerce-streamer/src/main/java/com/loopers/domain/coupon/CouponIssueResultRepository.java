package com.loopers.domain.coupon;

import java.util.Optional;

public interface CouponIssueResultRepository {
    Optional<CouponIssueResultModel> findByRequestId(String requestId);
    CouponIssueResultModel save(CouponIssueResultModel model);
}
