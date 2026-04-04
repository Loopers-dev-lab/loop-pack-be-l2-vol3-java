package com.loopers.application.order.queue;

public record OrderQueueStatusResult(
        boolean enabled,
        long waitingOrder,
        long estimatedWaitSeconds
) {
    public static OrderQueueStatusResult disabled() {
        return new OrderQueueStatusResult(false, 0L, 0L);
    }
}
