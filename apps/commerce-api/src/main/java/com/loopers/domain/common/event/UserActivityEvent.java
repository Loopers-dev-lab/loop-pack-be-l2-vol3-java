package com.loopers.domain.common.event;

import java.time.LocalDateTime;

/**
 * 유저 행동 이벤트 — "유저가 어떤 행동을 했다"
 *
 * 발행 시점: 행동 발생 즉시
 * 소비자: 유저 행동 로깅 (분석 저장소 append)
 *
 * 유실 허용 — 서비스 정합성에 영향 없음.
 * 추후 Kafka로 전환 시 Outbox 없이 직접 발행.
 */
public record UserActivityEvent(
        Long userId,
        String activityType,   // VIEW, CLICK, LIKE, ORDER, PAYMENT
        String targetType,     // PRODUCT, ORDER, COUPON
        Long targetId,
        LocalDateTime occurredAt
) {
    public static UserActivityEvent view(Long userId, Long productId) {
        return new UserActivityEvent(userId, "VIEW", "PRODUCT", productId, LocalDateTime.now());
    }

    public static UserActivityEvent like(Long userId, Long productId) {
        return new UserActivityEvent(userId, "LIKE", "PRODUCT", productId, LocalDateTime.now());
    }

    public static UserActivityEvent order(Long userId, Long orderId) {
        return new UserActivityEvent(userId, "ORDER", "ORDER", orderId, LocalDateTime.now());
    }

    public static UserActivityEvent payment(Long userId, Long orderId) {
        return new UserActivityEvent(userId, "PAYMENT", "ORDER", orderId, LocalDateTime.now());
    }
}
