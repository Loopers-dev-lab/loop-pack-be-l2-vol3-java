package com.loopers.application.process.checkout.event;

public record OrderCancelCompensationRequestedEvent(
        java.util.UUID orderId
) {
}
