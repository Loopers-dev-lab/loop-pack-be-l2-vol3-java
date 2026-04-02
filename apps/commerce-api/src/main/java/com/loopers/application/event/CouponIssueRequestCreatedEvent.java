package com.loopers.application.event;

import java.time.ZonedDateTime;

/**
 * 쿠폰 발급 요청 생성 이벤트.
 * 발급 요청이 생성되었을 때 발행됨.
 */
public record CouponIssueRequestCreatedEvent(
    String eventId,
    String requestId,
    Long couponId,
    Long userId,
    ZonedDateTime occurredAt
) {
    public static CouponIssueRequestCreatedEvent of(
        String eventId,
        String requestId,
        Long couponId,
        Long userId
    ) {
        return new CouponIssueRequestCreatedEvent(eventId, requestId, couponId, userId, ZonedDateTime.now());
    }
}
