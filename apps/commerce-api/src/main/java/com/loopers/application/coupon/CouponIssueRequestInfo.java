package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestStatus;

public record CouponIssueRequestInfo(
    Long requestId,
    Long templateId,
    CouponIssueRequestStatus status,
    String failReason
) {
    public static CouponIssueRequestInfo from(CouponIssueRequest request) {
        return new CouponIssueRequestInfo(
            request.getId(),
            request.getTemplateId(),
            request.getStatus(),
            request.getFailReason()
        );
    }
}
