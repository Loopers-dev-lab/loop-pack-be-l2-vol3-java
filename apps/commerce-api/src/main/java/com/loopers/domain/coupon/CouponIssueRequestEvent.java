package com.loopers.domain.coupon;

/**
 * 선착순 쿠폰 발급 요청 이벤트.
 * CouponFacade에서 발행 → BEFORE_COMMIT 리스너가 Outbox에 기록 → Kafka로 전달된다.
 */
public record CouponIssueRequestEvent(
        Long couponIssueResultId,
        Long couponTemplateId,
        Long userId
) {}
