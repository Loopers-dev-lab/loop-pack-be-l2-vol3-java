package com.loopers.domain.event;

public record PaymentFailedEvent(String orderId, Long paymentId, String reason) {
}
