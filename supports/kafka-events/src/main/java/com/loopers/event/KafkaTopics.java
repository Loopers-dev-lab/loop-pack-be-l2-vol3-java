package com.loopers.event;

/**
 * Kafka 토픽 이름 상수.
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    public static final String CATALOG_EVENTS = "catalog-events";
    public static final String ORDER_EVENTS = "order-events";
    public static final String COUPON_ISSUE_REQUESTS = "coupon-issue-requests";
}
