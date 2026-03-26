package com.loopers.domain.product.event;

/**
 * 상품 조회 이벤트.
 */
public record ProductViewedEvent(
        Long productId,
        Long userId
) {
}
