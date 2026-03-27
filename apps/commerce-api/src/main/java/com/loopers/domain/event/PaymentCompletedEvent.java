package com.loopers.domain.event;

public record PaymentCompletedEvent(String orderId, Long paymentId) {
}
