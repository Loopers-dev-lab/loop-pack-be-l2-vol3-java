package com.loopers.application.queue;

public record QueueInfo(
        QueueStatus status,
        long position,
        long estimatedWaitSeconds,
        long totalInQueue,
        String token
) {
}
