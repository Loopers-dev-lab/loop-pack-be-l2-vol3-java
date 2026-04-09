package com.loopers.domain.event;


public record PaymentFailedEvent(Long paymentId, Long orderId, Long userId, String reason) {
}
