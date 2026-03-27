package com.loopers.application.event;

import com.loopers.support.outbox.DomainEvent;

public record PaymentFailedEvent(Long paymentId, Long orderId, Long userId, String reason) implements DomainEvent {
}
