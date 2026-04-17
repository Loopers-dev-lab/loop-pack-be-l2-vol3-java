package com.loopers.confg.kafka;

public final class KafkaTopics {

    public static final String PRODUCT_VIEW_EVENTS = "product-view-events";
    public static final String PRODUCT_INTERACTION_EVENTS = "product-interaction-events";
    public static final String ORDER_EVENTS = "order-events";
    public static final String COUPON_ISSUE_REQUESTS = "coupon-issue-requests";

    private KafkaTopics() {
    }
}
