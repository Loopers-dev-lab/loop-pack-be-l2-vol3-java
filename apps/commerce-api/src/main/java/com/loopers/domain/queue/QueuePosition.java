package com.loopers.domain.queue;

public record QueuePosition(
    long position,
    long estimatedWaitSeconds,
    int retryAfter
) {
    public static QueuePosition of(long zeroBasedRank, int tps) {
        long position = zeroBasedRank + 1;
        long estimatedWaitSeconds = (tps > 0) ? position / tps : 0;
        int retryAfter = calculateRetryAfter(position);
        return new QueuePosition(position, estimatedWaitSeconds, retryAfter);
    }

    private static int calculateRetryAfter(long position) {
        if (position <= 100) return 1;
        if (position <= 1000) return 2;
        if (position <= 10000) return 3;
        return 5;
    }
}
