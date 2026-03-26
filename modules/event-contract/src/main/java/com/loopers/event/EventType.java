package com.loopers.event;

import com.loopers.event.payload.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum EventType {

    PRODUCT_LIKED(ProductLikedEventPayload.class, Topic.CATALOG_EVENTS),
    PRODUCT_UNLIKED(ProductUnlikedEventPayload.class, Topic.CATALOG_EVENTS),
    PRODUCT_VIEWED(ProductViewedEventPayload.class, Topic.CATALOG_EVENTS),
    ORDER_COMPLETED(OrderCompletedEventPayload.class, Topic.ORDER_EVENTS),
    PAYMENT_COMPLETED(PaymentCompletedEventPayload.class, Topic.ORDER_EVENTS),
    COUPON_ISSUE_REQUESTED(CouponIssueRequestedEventPayload.class, Topic.COUPON_ISSUE_REQUESTS),
    ;

    private final Class<? extends EventPayload> payloadClass;
    private final String topic;

    public static EventType from(String type) {
        return Arrays.stream(values())
                     .filter(e -> e.name().equals(type))
                     .findFirst()
                     .orElseThrow(() -> new IllegalArgumentException("Unknown EventType: " + type));
    }
}
