package com.loopers.domain.event;

public record FavoriteAddedEvent(
        Long productId,
        Long memberId) {
}
