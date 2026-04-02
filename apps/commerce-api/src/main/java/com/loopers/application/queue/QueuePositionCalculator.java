package com.loopers.application.queue;

import org.springframework.stereotype.Component;

import com.loopers.support.queue.QueueProperties;

import lombok.RequiredArgsConstructor;

/**
 * 대기열 순번(rank) 기반으로 {@link QueuePositionResult}를 생성한다.
 *
 * <p>예상 대기 시간과 권장 폴링 주기를 계산하여 결과에 포함한다.
 * 처리량은 {@link QueueProperties}에서 주입받아 스케줄러 설정과 단일 소스로 관리한다.</p>
 */
@Component
@RequiredArgsConstructor
public class QueuePositionCalculator {

    private final QueueProperties queueProperties;

    /**
     * 0-based rank와 전체 대기 인원으로부터 {@link QueuePositionResult}를 생성한다.
     *
     * @param rank         0-based 대기 순번
     * @param totalWaiting 전체 대기 인원
     * @return 1-based 순번, 예상 대기 시간, 폴링 주기가 포함된 결과
     */
    public QueuePositionResult calculate(long rank, long totalWaiting) {
        long position = rank + 1;
        long estimatedWaitSeconds = (long) Math.ceil(position / queueProperties.throughputPerSecond());
        long pollingIntervalMs = QueuePollingPolicy.calculateIntervalMs(position);
        return new QueuePositionResult(position, totalWaiting, estimatedWaitSeconds, pollingIntervalMs, null);
    }
}
