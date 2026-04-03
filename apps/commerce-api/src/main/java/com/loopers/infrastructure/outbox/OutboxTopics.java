package com.loopers.infrastructure.outbox;

public final class OutboxTopics {

    public static final String CATALOG_EVENTS = "catalog-events";
    public static final String ORDER_EVENTS = "order-events";
    public static final String COUPON_ISSUE_REQUESTS = "coupon-issue-requests";

    private OutboxTopics() {}
}
