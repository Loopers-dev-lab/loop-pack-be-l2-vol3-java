package com.loopers.application.product.event;

/**
 * 상품 좋아요 상태가 변경되었다는 사실 이벤트.
 * (캐시 무효화 등 커밋 이후 부가 처리를 위한 트리거)
 */
public record ProductLikeChangedEvent(Long productId) {
}

