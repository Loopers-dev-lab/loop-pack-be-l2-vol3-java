package com.loopers.application.event;


public record PaymentFailedEvent(Long paymentId, Long orderId, Long userId, String reason) {
}
