package com.loopers.domain.coupon;

import java.util.Optional;

public interface CouponIssueRequestRepository {
    Optional<CouponIssueRequest> findById(Long id);
}
