package com.loopers.domain.like;

public class ProductLikedEvent {

    private final Long productId;
    private final Long userId;

    public ProductLikedEvent(Long productId, Long userId) {
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
