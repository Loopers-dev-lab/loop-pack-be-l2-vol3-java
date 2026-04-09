package com.loopers.application.process.checkout.event;

import java.util.UUID;

public record OrderPaymentCancelRequestEvent(
        String memberId,
        UUID orderId
) {
}
