package com.loopers.interfaces.consumer;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Topics {

    public static final String LIKED = "like-liked-v1";
    public static final String UNLIKED = "like-unliked-v1";
    public static final String ORDER_COMPLETED = "order-completed-v1";
    public static final String PRODUCT_VIEWED = "product-viewed-v1";
    public static final String PRODUCT_DELETED = "product-deleted-v1";
}
