package com.loopers.domain.coupon;

import java.util.Optional;

public interface CouponIssueRequestRepository {

    Long save(CouponIssueRequest request);

    Optional<CouponIssueRequest> findByRequestId(String requestId);
}
