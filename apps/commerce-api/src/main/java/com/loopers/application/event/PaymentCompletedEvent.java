package com.loopers.application.event;

import com.loopers.support.outbox.DomainEvent;

import java.math.BigDecimal;

public record PaymentCompletedEvent(Long paymentId, Long orderId, Long userId, BigDecimal amount) implements DomainEvent {
}
