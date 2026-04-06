package com.loopers.application.queue;

public record QueueResult(
    Long userId,
    int position,
    int estimatedWaitSeconds,
    QueueStatus status,
    String token
) {
    public static QueueResult waiting(Long userId, int position, int estimatedWaitSeconds) {
        return new QueueResult(userId, position, estimatedWaitSeconds, QueueStatus.WAITING, null);
    }

    public static QueueResult alreadyInQueue(Long userId, int position, int estimatedWaitSeconds) {
        return new QueueResult(userId, position, estimatedWaitSeconds, QueueStatus.ALREADY_IN_QUEUE, null);
    }

    public static QueueResult tokenIssued(Long userId, String token) {
        return new QueueResult(userId, 0, 0, QueueStatus.TOKEN_ISSUED, token);
    }
}