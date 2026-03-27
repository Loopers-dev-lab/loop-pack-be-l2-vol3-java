package com.loopers.interfaces.consumer.dto;

public record CouponIssueMessage(
        Long requestId,
        Long couponTemplateId,
        Long memberId) {
}
