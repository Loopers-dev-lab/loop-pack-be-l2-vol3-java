package com.loopers.application.coupon.dto;

import com.loopers.domain.coupon.model.CouponIssueRequest;
import com.loopers.domain.coupon.model.CouponIssueStatus;

public record CouponIssueRequestResDto(Long requestId, Long couponTemplateId, CouponIssueStatus status) {

    public static CouponIssueRequestResDto from(CouponIssueRequest request) {
        return new CouponIssueRequestResDto(request.getId(), request.getCouponTemplateId(), request.getStatus());
    }
}
