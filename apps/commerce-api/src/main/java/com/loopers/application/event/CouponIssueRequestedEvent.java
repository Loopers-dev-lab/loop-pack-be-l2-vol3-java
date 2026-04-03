package com.loopers.application.event;


public record CouponIssueRequestedEvent(String eventId, Long couponId, Long userId) {
}
