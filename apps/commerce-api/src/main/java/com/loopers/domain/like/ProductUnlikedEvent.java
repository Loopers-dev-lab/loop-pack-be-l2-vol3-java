package com.loopers.domain.like;

public class ProductUnlikedEvent {

    private final Long productId;
    private final Long userId;

    public ProductUnlikedEvent(Long productId, Long userId) {
        this.productId = productId;
        this.userId = userId;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getUserId() {
        return userId;
    }
}
