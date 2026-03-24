package com.loopers.domain.coupon.event;

import java.time.LocalDateTime;

public record CouponIssueRequestedEvent(
        String eventId,
        String requestId,
        Long couponTemplateId,
        Long memberId,
        LocalDateTime requestedAt
) {
}
