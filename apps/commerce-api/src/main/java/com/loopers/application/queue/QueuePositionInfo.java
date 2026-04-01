package com.loopers.application.queue;

public record QueuePositionInfo(
        Long rank,
        Long totalWaiting,
        Long estimatedWaitSeconds,
        String token
) {
    public static QueuePositionInfo waiting(Long rank, Long totalWaiting, Long estimatedWaitSeconds) {
        return new QueuePositionInfo(rank, totalWaiting, estimatedWaitSeconds, null);
    }

    public static QueuePositionInfo ready(Long totalWaiting, String token) {
        return new QueuePositionInfo(0L, totalWaiting, 0L, token);
    }
}
