package com.loopers.domain.like.event;

public record ProductLikedEvent(
        Long memberId,
        Long productId
) {
    public static ProductLikedEvent from(Long memberId, Long productId) {
        return new ProductLikedEvent(memberId, productId);
    }
}
