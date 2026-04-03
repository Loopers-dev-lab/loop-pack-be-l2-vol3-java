package com.loopers.domain.queue;

/**
 * 예상 대기 시간(초). 설계: {@code ceil(max(position,0) / throughputTps) + 1} (Jitter 평균 1초).
 *
 * <p>{@code throughputTps}는 양의 유한값이어야 한다. 애플리케이션 설정({@code queue.position.throughput-tps})에서도 동일 범위를 검증한다.
 */
public final class QueuePositionEstimator {

    private QueuePositionEstimator() {
    }

    public static long estimatedWaitSeconds(long position, double throughputTps) {
        if (!(throughputTps > 0) || !Double.isFinite(throughputTps)) {
            throw new IllegalArgumentException(
                    "throughputTps must be finite and positive, got: " + throughputTps);
        }
        long p = Math.max(0L, position);
        return (long) Math.ceil(p / throughputTps) + 1;
    }
}
