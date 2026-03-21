package com.loopers.application.order.event;

import java.util.UUID;

public record OrderPaymentCancelRequestEvent(
        String memberId,
        UUID orderId
) {
}
