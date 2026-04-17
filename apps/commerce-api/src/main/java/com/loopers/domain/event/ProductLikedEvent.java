package com.loopers.domain.event;


public record ProductLikedEvent(Long userId, Long productId) {
}
