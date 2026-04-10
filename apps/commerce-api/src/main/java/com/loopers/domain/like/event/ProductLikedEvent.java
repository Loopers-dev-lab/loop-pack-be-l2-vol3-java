package com.loopers.domain.like.event;

/**
 * 상품 좋아요 등록 이벤트.
 */
public record ProductLikedEvent(
        Long userId,
        Long productId
) {
}
