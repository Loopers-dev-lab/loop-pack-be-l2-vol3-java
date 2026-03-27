package com.loopers.application.event;

import com.loopers.support.outbox.DomainEvent;

public record ProductUnlikedEvent(Long userId, Long productId) implements DomainEvent {
}
