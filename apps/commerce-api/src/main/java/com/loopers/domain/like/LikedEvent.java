package com.loopers.domain.like;

public class LikedEvent {

    private final Long productId;

    public LikedEvent(Long productId) {
        this.productId = productId;
    }

    public Long getProductId() {
        return productId;
    }
}
