package com.loopers.domain.product.event;

public record ProductViewedEvent(
        Long productId
) {
    public static ProductViewedEvent from(Long productId) {
        return new ProductViewedEvent(productId);
    }
}
