package com.loopers.domain.queue;

/**
 * 예상 대기 시간(초). 설계: {@code ceil(position / max(throughputTps, ε)) + 1} (Jitter 평균 1초).
 */
public final class QueuePositionEstimator {

    private static final double EPS = 0.001;

    private QueuePositionEstimator() {
    }

    public static long estimatedWaitSeconds(long position, double throughputTps) {
        double denom = Math.max(throughputTps, EPS);
        return (long) Math.ceil(position / denom) + 1;
    }
}
