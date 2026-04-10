package com.loopers.domain.coupon;

import java.time.LocalDateTime;

/**
 * 선착순 쿠폰 발급 요청 메시지 (Kafka Producer/Consumer 공용).
 */
public record CouponIssueRequestMessage(
        String requestId,
        Long userId,
        Long couponId,
        LocalDateTime requestedAt
) {
}
