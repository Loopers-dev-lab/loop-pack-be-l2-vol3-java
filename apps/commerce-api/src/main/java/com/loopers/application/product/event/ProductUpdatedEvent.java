package com.loopers.application.product.event;

/**
 * 상품이 수정되었다는 사실 이벤트.
 */
public record ProductUpdatedEvent(Long productId) {
}

