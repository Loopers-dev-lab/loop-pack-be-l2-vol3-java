package com.loopers.application.coupon.view;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record CouponIssueRequestView(
        UUID requestId,
        UUID couponId,
        CouponIssueRequestStatus status,
        String failureReason,
        LocalDateTime requestedAt,
        LocalDateTime processedAt
) {
    public static CouponIssueRequestView from(CouponIssueRequest request) {
        return new CouponIssueRequestView(
                request.requestId(),
                request.couponId(),
                request.status(),
                request.failureReason(),
                request.requestedAt(),
                request.processedAt()
        );
    }
}
