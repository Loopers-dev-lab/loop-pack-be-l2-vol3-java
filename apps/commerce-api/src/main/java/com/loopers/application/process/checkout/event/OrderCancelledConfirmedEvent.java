package com.loopers.application.process.checkout.event;

import java.util.UUID;

public record OrderCancelledConfirmedEvent(
        UUID orderId,
        String memberId
) {
}
