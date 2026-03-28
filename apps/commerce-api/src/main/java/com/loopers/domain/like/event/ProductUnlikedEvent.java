package com.loopers.domain.like.event;

public record ProductUnlikedEvent(
        Long memberId,
        Long productId
) {
    public static ProductUnlikedEvent from(Long memberId, Long productId) {
        return new ProductUnlikedEvent(memberId, productId);
    }
}
