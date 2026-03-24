package com.loopers.application.coupon;

import java.time.LocalDateTime;

public record CouponIssueOutboxPayload(
        String eventId,
        String eventType,
        int schemaVersion,
        String requestId,
        Long couponTemplateDbId,
        Long memberId,
        LocalDateTime requestedAt
) {
}
