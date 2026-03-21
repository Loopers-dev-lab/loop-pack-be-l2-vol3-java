package com.loopers.application.payment.event;

import com.loopers.domain.payment.PaymentStatus;

import java.util.UUID;

public record PaymentStatusChangedEvent(
        String memberId,
        UUID orderId,
        PaymentStatus beforeStatus,
        PaymentStatus afterStatus
) {
}
