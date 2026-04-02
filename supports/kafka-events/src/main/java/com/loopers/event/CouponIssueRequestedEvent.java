package com.loopers.event;

import java.time.Instant;

/**
 * 쿠폰 발급 요청 이벤트.
 * Producer/Consumer 간 공유되는 이벤트 DTO.
 */
public record CouponIssueRequestedEvent(
    String eventId,
    String requestId,
    Long couponId,
    Long userId,
    Instant occurredAt
) {

    public static CouponIssueRequestedEvent of(
        String eventId,
        String requestId,
        Long couponId,
        Long userId
    ) {
        return new CouponIssueRequestedEvent(
            eventId,
            requestId,
            couponId,
            userId,
            Instant.now()
        );
    }
}
