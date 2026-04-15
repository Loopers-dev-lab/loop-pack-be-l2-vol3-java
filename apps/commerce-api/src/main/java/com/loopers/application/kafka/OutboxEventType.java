package com.loopers.application.kafka;

public final class OutboxEventType {

    private OutboxEventType() {}

    public static final String PRODUCT_LIKED = "PRODUCT_LIKED";
    public static final String PRODUCT_UNLIKED = "PRODUCT_UNLIKED";
    public static final String ORDER_COMPLETED = "ORDER_COMPLETED";
    public static final String PRODUCT_VIEWED = "PRODUCT_VIEWED";
}
