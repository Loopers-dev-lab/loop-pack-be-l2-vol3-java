package com.loopers.application.event;

import java.time.ZonedDateTime;
import java.util.List;

public class AppEvents {

    public record OrderPlacedApplicationEvent(
        String eventId,
        Long orderId,
        Long userId,
        ZonedDateTime occurredAt,
        List<OrderItemPayload> items,
        Long totalAmount
    ) {
    }

    public record OrderItemPayload(Long productId, Integer quantity) {
    }

    public record ProductLikeChangedApplicationEvent(
        String eventId,
        Long productId,
        Long userId,
        Integer delta,
        ZonedDateTime occurredAt
    ) {
    }

    public record ProductViewedApplicationEvent(
        String eventId,
        Long productId,
        Long userId,
        ZonedDateTime occurredAt
    ) {
    }

    public record ProductClickedApplicationEvent(
        String eventId,
        Long productId,
        Long userId,
        ZonedDateTime occurredAt
    ) {
    }

    public record CouponIssueRequestedApplicationEvent(
        String eventId,
        Long requestId,
        Long couponId,
        Long userId,
        ZonedDateTime occurredAt
    ) {
    }
}
