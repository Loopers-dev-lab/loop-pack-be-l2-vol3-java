package com.loopers.application.process.checkout.event;

import java.util.UUID;

public record OrderCancelCompensationStepCompletedEvent(
        UUID orderId,
        StepType stepType
) {
    public enum StepType {
        COUPON,
        POINT,
        STOCK
    }
}
