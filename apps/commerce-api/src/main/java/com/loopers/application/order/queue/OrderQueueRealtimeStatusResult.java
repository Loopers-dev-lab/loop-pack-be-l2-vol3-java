package com.loopers.application.order.queue;

public record OrderQueueRealtimeStatusResult(
        boolean enabled,
        long rank,
        long displayWaitingOrder,
        long estimatedWaitSeconds,
        long recommendedPollingIntervalSeconds,
        String admissionState
) {
    public static OrderQueueRealtimeStatusResult disabled() {
        return new OrderQueueRealtimeStatusResult(false, -1L, 0L, 0L, 0L, "DISABLED");
    }
}
