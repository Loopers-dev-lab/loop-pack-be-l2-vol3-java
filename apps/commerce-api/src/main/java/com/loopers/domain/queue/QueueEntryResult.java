package com.loopers.domain.queue;

public record QueueEntryResult(
        long position,
        long totalWaiting,
        long estimatedWaitSeconds,
        boolean isNew
) {
}
