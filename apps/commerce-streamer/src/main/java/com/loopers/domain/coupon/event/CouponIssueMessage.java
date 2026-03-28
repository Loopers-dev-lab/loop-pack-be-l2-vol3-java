package com.loopers.domain.coupon.event;

import java.time.LocalDateTime;

public record CouponIssueMessage(
        String requestId,
        Long couponId,
        Long memberId,
        LocalDateTime requestedAt
) {}
