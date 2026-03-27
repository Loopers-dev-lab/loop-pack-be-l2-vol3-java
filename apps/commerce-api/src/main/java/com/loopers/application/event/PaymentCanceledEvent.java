package com.loopers.application.event;

public record PaymentCanceledEvent(Long paymentId, Long orderId, Long userId) {
}
