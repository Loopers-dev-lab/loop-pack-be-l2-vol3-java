package com.loopers.application.event;

import java.math.BigDecimal;

public record PaymentCompletedEvent(Long paymentId, Long orderId, Long userId, BigDecimal amount) {
}
