package com.loopers.domain.fcfs;

import java.util.Optional;

/**
 * 쿠폰 발급 요청 Repository 인터페이스.
 */
public interface CouponIssueRequestRepository {

    CouponIssueRequest save(CouponIssueRequest request);

    Optional<CouponIssueRequest> findByRequestId(String requestId);

    void updateStatus(String requestId, CouponIssueRequestStatus status, String failureReason);

    long countByStatus(CouponIssueRequestStatus status);

    long countByCouponIdAndStatus(Long couponId, CouponIssueRequestStatus status);
}
