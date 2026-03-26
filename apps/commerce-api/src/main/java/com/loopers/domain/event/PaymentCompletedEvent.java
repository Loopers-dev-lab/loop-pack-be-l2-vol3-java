package com.loopers.domain.event;

public record PaymentCompletedEvent(Long orderId, Long userId, String transactionKey, String message) {
}
