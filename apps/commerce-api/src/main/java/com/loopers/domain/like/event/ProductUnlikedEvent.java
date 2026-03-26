package com.loopers.domain.like.event;

/**
 * 상품 좋아요 취소 이벤트.
 */
public record ProductUnlikedEvent(
        Long userId,
        Long productId
) {
}
