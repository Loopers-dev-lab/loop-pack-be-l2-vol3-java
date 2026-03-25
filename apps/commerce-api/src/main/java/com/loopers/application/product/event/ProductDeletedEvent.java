package com.loopers.application.product.event;

/**
 * 상품이 삭제되었다는 사실 이벤트.
 */
public record ProductDeletedEvent(Long productId) {
}

