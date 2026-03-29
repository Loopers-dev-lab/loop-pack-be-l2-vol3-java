package com.loopers.domain.event;

import java.time.ZonedDateTime;

public record PaymentCompletedEvent(
        Long paymentId,
        Long orderId,
        Long userId,
        ZonedDateTime occurredAt
) {
}
