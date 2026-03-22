package com.loopers.interfaces.consumer;

import java.time.LocalDateTime;

public record CouponIssuePayload(
        String eventId,
        String eventType,
        int schemaVersion,
        String requestId,
        Long requestDbId,
        Long couponTemplateDbId,
        Long memberId,
        LocalDateTime requestedAt
) {
}
