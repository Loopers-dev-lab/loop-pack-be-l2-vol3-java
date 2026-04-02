package com.loopers.domain.queue;

public record QueuePosition(long rank) {

    public long estimatedWaitSeconds(long schedulerIntervalMs, int batchSize) {
        return rank * (schedulerIntervalMs / 1000L) / batchSize;
    }

    public long nextPollAfter(long schedulerIntervalMs, int batchSize) {
        long wait = estimatedWaitSeconds(schedulerIntervalMs, batchSize);
        if (wait < 30) return 1L;
        if (wait < 120) return 3L;
        return 5L;
    }
}
