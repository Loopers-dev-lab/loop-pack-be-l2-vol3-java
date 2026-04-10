package com.loopers.domain.event;

public record ProductViewedEvent(Long productId, Long memberId) {
}
