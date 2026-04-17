package com.loopers.domain.event;

public record LikeCreatedEvent(long productId, long memberId) {
}
