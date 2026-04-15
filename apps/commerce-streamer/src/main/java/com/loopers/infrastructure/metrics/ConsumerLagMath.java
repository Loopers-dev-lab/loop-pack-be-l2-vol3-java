package com.loopers.infrastructure.metrics;

/**
 * 파티션 단위 lag = logEndOffset - committedOffset (Kafka next-offset 의미와 정합).
 */
public final class ConsumerLagMath {

    private ConsumerLagMath() {}

    public static long partitionLag(long logEndOffset, long committedNextOffset) {
        if (committedNextOffset < 0) {
            return Math.max(0L, logEndOffset);
        }
        return Math.max(0L, logEndOffset - committedNextOffset);
    }
}
