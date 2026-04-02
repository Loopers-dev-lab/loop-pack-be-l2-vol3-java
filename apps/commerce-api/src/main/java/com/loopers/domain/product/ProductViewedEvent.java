package com.loopers.domain.product;

public class ProductViewedEvent {

    private final Long productId;

    public ProductViewedEvent(Long productId) {
        this.productId = productId;
    }

    public Long getProductId() {
        return productId;
    }
}
