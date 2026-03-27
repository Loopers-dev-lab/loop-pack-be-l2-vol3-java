package com.loopers.domain.event;

public record ProductViewedEvent(long productId, long memberId) {
}
