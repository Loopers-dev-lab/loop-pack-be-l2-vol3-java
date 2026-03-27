package com.loopers.application.event;

import com.loopers.support.outbox.DomainEvent;

public record CouponIssueRequestedEvent(String eventId, Long couponId, Long userId) implements DomainEvent {
}
