package com.loopers.application.event;

import com.loopers.support.outbox.DomainEvent;

public record PaymentCanceledEvent(Long paymentId, Long orderId, Long userId) implements DomainEvent {
}
