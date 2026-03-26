package com.loopers.interfaces.consumer.payload;

public record CouponIssuePayload(String eventId, String requestId, Long couponId, Long userId) {
}
