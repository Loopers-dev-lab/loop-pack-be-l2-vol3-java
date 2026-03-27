package com.loopers.infrastructure.coupon.kafka;

import java.time.LocalDateTime;

/**
 * 쿠폰 발급 Kafka 메시지 페이로드.
 *
 * @param couponId    발급된 쿠폰 ID
 * @param userId      발급 대상 사용자 ID
 * @param requestedAt 발급 요청 시각
 */
public record CouponIssuedPayload(
        Long couponId,
        Long userId,
        LocalDateTime requestedAt
) {
}
