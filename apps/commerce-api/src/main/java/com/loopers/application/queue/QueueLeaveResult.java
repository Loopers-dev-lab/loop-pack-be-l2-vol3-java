package com.loopers.application.queue;

public record QueueLeaveResult(
    Long userId,
    QueueLeaveStatus status
) {
    public static QueueLeaveResult left(Long userId) {
        return new QueueLeaveResult(userId, QueueLeaveStatus.LEFT);
    }

    public static QueueLeaveResult notInQueue(Long userId) {
        return new QueueLeaveResult(userId, QueueLeaveStatus.NOT_IN_QUEUE);
    }
}