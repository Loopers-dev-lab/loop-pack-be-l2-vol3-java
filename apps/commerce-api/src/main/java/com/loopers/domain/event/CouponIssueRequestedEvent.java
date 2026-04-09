package com.loopers.domain.event;


public record CouponIssueRequestedEvent(String eventId, Long couponId, Long userId) {
}
