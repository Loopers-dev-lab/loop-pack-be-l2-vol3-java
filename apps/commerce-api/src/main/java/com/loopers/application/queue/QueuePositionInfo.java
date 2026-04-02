package com.loopers.application.queue;

public record QueuePositionInfo(
    Long userId,
    QueueStatus status,
    Long position,
    Long totalWaiting,
    Long estimatedWaitSeconds,
    String token,
    Integer recommendedPollingSeconds
) {
    public static QueuePositionInfo notInQueue(Long userId, Long totalWaiting) {
        return new QueuePositionInfo(userId, QueueStatus.NOT_IN_QUEUE, null, totalWaiting, null, null, 15);
    }
}
