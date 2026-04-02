package com.loopers.event;

/**
 * 시스템 간 전파되는 이벤트 타입.
 */
public enum EventType {
    LIKE_CREATED,
    LIKE_CANCELED,
    ORDER_COMPLETED,
    PRODUCT_VIEWED,
    COUPON_ISSUE_REQUESTED
}
