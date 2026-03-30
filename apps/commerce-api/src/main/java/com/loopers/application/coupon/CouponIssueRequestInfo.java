package com.loopers.application.coupon;

import com.loopers.domain.event.CouponIssueRequestModel;
import com.loopers.domain.event.CouponIssueRequestStatus;

import java.time.ZonedDateTime;

public record CouponIssueRequestInfo(
    Long requestId,
    Long couponId,
    Long userId,
    CouponIssueRequestStatus status,
    String failureReason,
    ZonedDateTime requestedAt,
    ZonedDateTime updatedAt
) {
    public static CouponIssueRequestInfo from(CouponIssueRequestModel model) {
        return new CouponIssueRequestInfo(
            model.getId(),
            model.getCouponId(),
            model.getUserId(),
            model.getStatus(),
            model.getFailureReason(),
            model.getCreatedAt(),
            model.getUpdatedAt()
        );
    }
}
