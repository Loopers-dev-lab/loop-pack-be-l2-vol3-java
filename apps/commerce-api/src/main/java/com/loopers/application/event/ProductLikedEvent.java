package com.loopers.application.event;

import com.loopers.support.outbox.DomainEvent;

public record ProductLikedEvent(Long userId, Long productId) implements DomainEvent {
}
