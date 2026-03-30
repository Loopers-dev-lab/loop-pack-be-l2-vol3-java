package com.loopers.application.queue;

/**
 * 대기열 순번 조회 결과.
 *
 * @param position             1-based 대기 순번
 * @param totalWaiting         전체 대기 인원
 * @param estimatedWaitSeconds 예상 대기 시간 (초)
 */
public record QueuePositionResult(
        long position,
        long totalWaiting,
        long estimatedWaitSeconds
) {

}
