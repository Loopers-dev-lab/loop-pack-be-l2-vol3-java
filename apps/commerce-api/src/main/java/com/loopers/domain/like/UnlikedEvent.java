package com.loopers.domain.like;

public class UnlikedEvent {

    private final Long productId;

    public UnlikedEvent(Long productId) {
        this.productId = productId;
    }

    public Long getProductId() {
        return productId;
    }
}
