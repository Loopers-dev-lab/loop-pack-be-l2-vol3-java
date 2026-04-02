package com.loopers.application.queue;

import lombok.experimental.UtilityClass;

/**
 * 대기열 순번(rank) 기반으로 {@link QueuePositionResult}를 생성한다.
 *
 * <p>예상 대기 시간과 권장 폴링 주기를 계산하여 결과에 포함한다.</p>
 */
@UtilityClass
public class QueuePositionCalculator {

    static final long THROUGHPUT_PER_SECOND = 5;

    /**
     * 0-based rank와 전체 대기 인원으로부터 {@link QueuePositionResult}를 생성한다.
     *
     * @param rank         0-based 대기 순번
     * @param totalWaiting 전체 대기 인원
     * @return 1-based 순번, 예상 대기 시간, 폴링 주기가 포함된 결과
     */
    public static QueuePositionResult calculate(long rank, long totalWaiting) {
        long position = rank + 1;
        long estimatedWaitSeconds = (long) Math.ceil((double) position / THROUGHPUT_PER_SECOND);
        long pollingIntervalMs = QueuePollingPolicy.calculateIntervalMs(position);
        return new QueuePositionResult(position, totalWaiting, estimatedWaitSeconds, pollingIntervalMs, null);
    }
}
