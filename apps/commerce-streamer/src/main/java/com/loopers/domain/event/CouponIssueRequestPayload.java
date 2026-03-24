package com.loopers.domain.event;

import java.time.ZonedDateTime;

public record CouponIssueRequestPayload(
    String requestId,
    Long couponId,
    Long userId,
    ZonedDateTime occurredAt
) {
}
