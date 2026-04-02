package com.loopers.application.queue;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.support.queue.WaitingQueue;

import lombok.RequiredArgsConstructor;

/**
 * 사용자가 대기열에 진입합니다.
 *
 * <p>멱등 연산으로, 이미 대기열에 존재하는 사용자가 재진입을 시도해도 정상 처리됩니다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class EnterQueueUseCase {

    private final WaitingQueue waitingQueue;

    /**
     * @param userId 대기열에 진입할 사용자 ID
     * @return 현재 대기 순번 정보
     */
    public QueuePositionResult execute(Long userId) {
        waitingQueue.enter(userId);
        Long rank = waitingQueue.getPosition(userId);
        long totalWaiting = waitingQueue.getTotalCount();
        return QueuePositionCalculator.calculate(rank, totalWaiting);
    }
}
