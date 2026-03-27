package com.loopers.domain.coupon;

import java.util.Optional;

public interface CouponIssueRequestRepository {

    Optional<CouponIssueRequest> findByEventId(String eventId);

    CouponIssueRequest save(CouponIssueRequest request);

    int issueIfAvailable(Long couponId);
}
