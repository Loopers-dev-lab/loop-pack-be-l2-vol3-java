package com.loopers.application.queue;

public record QueueInfo(
    long position,
    long totalWaiting,
    long estimatedWaitSeconds,
    String token,
    Long tokenExpiresIn
) {
    public static QueueInfo from(QueueService.QueueStatus status) {
        return new QueueInfo(status.position(), status.totalWaiting(), status.estimatedWaitSeconds(), null, null);
    }

    public static QueueInfo withToken(QueueService.QueueStatus status, String token, Long expiresIn) {
        return new QueueInfo(status.position(), status.totalWaiting(), status.estimatedWaitSeconds(), token, expiresIn);
    }
}
