package com.loopers.domain.outbox;

public interface OutboxEventTopics {
    String PRODUCT_LIKE = "product.like.events";
    String PRODUCT_PAYMENT = "product.payment.events";
    String PRODUCT_VIEW = "product.view.events";
    String COUPON_ISSUE = "coupon.issue.requests";
}