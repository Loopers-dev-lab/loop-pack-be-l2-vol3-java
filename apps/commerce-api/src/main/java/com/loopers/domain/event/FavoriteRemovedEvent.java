package com.loopers.domain.event;

public record FavoriteRemovedEvent(Long productId, Long memberId) {
}
