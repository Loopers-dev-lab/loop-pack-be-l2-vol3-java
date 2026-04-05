package com.loopers.application.queue;

import com.loopers.domain.queue.QueueEntryResult;

public record QueueInfo(
        Long userId,
        long position,
        long totalWaiting,
        boolean newEntry
) {
    public static QueueInfo from(QueueEntryResult result) {
        return new QueueInfo(result.userId(), result.position(), result.totalWaiting(), result.newEntry());
    }
}
