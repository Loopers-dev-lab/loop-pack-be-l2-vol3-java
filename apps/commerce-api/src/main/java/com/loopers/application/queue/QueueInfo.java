package com.loopers.application.queue;

public record QueueInfo(
        QueueStatus status,
        long position,
        long estimatedWaitSeconds,
        long totalInQueue,
        String token,
        Boolean schedulerHealthy
) {
    public QueueInfo(QueueStatus status, long position, long estimatedWaitSeconds, long totalInQueue, String token) {
        this(status, position, estimatedWaitSeconds, totalInQueue, token, null);
    }
}
