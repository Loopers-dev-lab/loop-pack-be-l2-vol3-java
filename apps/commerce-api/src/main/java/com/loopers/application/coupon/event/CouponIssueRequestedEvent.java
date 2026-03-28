package com.loopers.application.coupon.event;

import java.time.ZonedDateTime;

public record CouponIssueRequestedEvent(
    String requestId,
    Long couponId,
    Long userId,
    ZonedDateTime occurredAt
) {
}
