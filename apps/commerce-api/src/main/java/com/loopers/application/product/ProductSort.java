package com.loopers.application.product;

import com.loopers.domain.product.ProductOrder;

public enum ProductSort {
    LATEST, PRICE_ASC, LIKES_DESC;

    public ProductOrder toOrder() {
        return switch (this) {
            case LATEST -> ProductOrder.LATEST;
            case PRICE_ASC -> ProductOrder.PRICE_ASC;
            case LIKES_DESC -> ProductOrder.LIKES_DESC;
        };
    }
}
