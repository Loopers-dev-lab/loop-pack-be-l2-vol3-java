package com.loopers.domain.coupon;

import com.loopers.domain.outbox.OutboxEvent;

import java.time.ZonedDateTime;

public record CouponIssueRequestMessage(
        String eventId,
        String eventType,
        String aggregateId,
        String payload,
        ZonedDateTime occurredAt
) {
    public static CouponIssueRequestMessage from(OutboxEvent outboxEvent) {
        return new CouponIssueRequestMessage(
                outboxEvent.getEventId(),
                outboxEvent.getEventType(),
                outboxEvent.getAggregateId(),
                outboxEvent.getPayload(),
                outboxEvent.getOccurredAt()
        );
    }
}
