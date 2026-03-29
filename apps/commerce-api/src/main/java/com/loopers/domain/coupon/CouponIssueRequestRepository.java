package com.loopers.domain.coupon;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface CouponIssueRequestRepository {
    CouponIssueRequest save(CouponIssueRequest request);

    Optional<CouponIssueRequest> findByRequestIdAndMemberId(UUID requestId, String memberId);

    Optional<CouponIssueRequest> findByRequestId(UUID requestId);

    int markProcessing(UUID requestId);

    int markSucceeded(UUID requestId, LocalDateTime processedAt);

    int markFailed(UUID requestId, CouponIssueRequestStatus status, String failureReason, LocalDateTime processedAt);
}
