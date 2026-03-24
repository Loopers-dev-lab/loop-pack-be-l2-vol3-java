package com.loopers.application.payment.event;

import java.time.ZonedDateTime;

public record PaymentFailedEvent(
    Long paymentId,
    Long orderId,
    Long userId,
    ZonedDateTime occurredAt
) {
}
