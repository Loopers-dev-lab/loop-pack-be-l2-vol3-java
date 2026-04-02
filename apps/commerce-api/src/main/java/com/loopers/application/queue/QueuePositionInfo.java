package com.loopers.application.queue;

import com.loopers.domain.queue.QueuePositionResult;

public record QueuePositionInfo(
        long position,
        long totalWaiting,
        long estimatedWaitSeconds,
        int pollingIntervalSeconds,
        String token
) {
    public static QueuePositionInfo from(QueuePositionResult result) {
        return new QueuePositionInfo(
                result.position(), result.totalWaiting(),
                result.estimatedWaitSeconds(), result.pollingIntervalSeconds(),
                result.token()
        );
    }
}
