package com.loopers.domain.model.like.event;

import com.loopers.domain.model.common.DomainEvent;

import java.time.LocalDateTime;

public record ProductLikedEvent(
        Long productId,
        LocalDateTime occurredAt
) implements DomainEvent {

    public ProductLikedEvent(Long productId) {
        this(productId, LocalDateTime.now());
    }
}
