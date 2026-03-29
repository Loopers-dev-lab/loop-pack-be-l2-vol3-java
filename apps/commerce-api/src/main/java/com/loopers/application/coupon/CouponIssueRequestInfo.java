package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequestModel;

import java.time.ZonedDateTime;

public record CouponIssueRequestInfo(
        String requestId,
        Long couponId,
        Long userId,
        String status,
        ZonedDateTime requestedAt,
        Long issuedCouponId
) {
    public static CouponIssueRequestInfo from(CouponIssueRequestModel model) {
        if (model == null) {
            return null;
        }
        return new CouponIssueRequestInfo(
                model.getRequestId(),
                model.getCouponTemplateId(),
                model.getUserId(),
                model.getStatus().name(),
                model.getCreatedAt(),
                model.getIssuedCouponId()
        );
    }
}
