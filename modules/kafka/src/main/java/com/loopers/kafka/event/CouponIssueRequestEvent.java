package com.loopers.kafka.event;

public record CouponIssueRequestEvent(
    String eventId,           // 멱등성 키 (UUID)
    Long memberId,
    Long couponTemplateId,
    long requestedAt          // epoch millis
) {}
