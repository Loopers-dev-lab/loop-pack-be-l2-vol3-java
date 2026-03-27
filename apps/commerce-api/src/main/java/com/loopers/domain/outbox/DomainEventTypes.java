package com.loopers.domain.outbox;

/**
 * Outbox event_type 및 Consumer 라우팅용 상수.
 */
public final class DomainEventTypes {

    public static final String PRODUCT_LIKE_CHANGED = "PRODUCT_LIKE_CHANGED";
    public static final String PRODUCT_VIEWED = "PRODUCT_VIEWED";

    public static final String PAYMENT_COMPLETED = "PAYMENT_COMPLETED";

    public static final String USER_REGISTERED = "USER_REGISTERED";
    public static final String BRAND_REGISTERED = "BRAND_REGISTERED";
    public static final String CART_ITEM_ADDED = "CART_ITEM_ADDED";

    private DomainEventTypes() {
    }
}
