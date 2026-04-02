package com.loopers.domain.queue;

/**
 * 대기열 진입 결과
 */
public record QueueEntryResult(
        QueueStatus status,
        long position,
        long totalWaiting,
        int estimatedWaitSeconds
) {

    public static QueueEntryResult waiting(long position, long totalWaiting, int estimatedWaitSeconds) {
        return new QueueEntryResult(QueueStatus.WAITING, position, totalWaiting, estimatedWaitSeconds);
    }

    public static QueueEntryResult alreadyActive() {
        return new QueueEntryResult(QueueStatus.ACTIVE, 0, 0, 0);
    }
}
