package com.loopers.application.queue;

public record QueuePositionResult(
    Long userId,
    int position,
    int estimatedWaitSeconds,
    long totalWaiting,
    QueueStatus status,
    String token
) {
    public static QueuePositionResult waiting(Long userId, int position, int estimatedWaitSeconds, long totalWaiting) {
        return new QueuePositionResult(userId, position, estimatedWaitSeconds, totalWaiting, QueueStatus.WAITING, null);
    }

    public static QueuePositionResult tokenIssued(Long userId, String token, long totalWaiting) {
        return new QueuePositionResult(userId, 0, 0, totalWaiting, QueueStatus.TOKEN_ISSUED, token);
    }

    public static QueuePositionResult notInQueue(Long userId, long totalWaiting) {
        return new QueuePositionResult(userId, -1, 0, totalWaiting, QueueStatus.NOT_IN_QUEUE, null);
    }
}
