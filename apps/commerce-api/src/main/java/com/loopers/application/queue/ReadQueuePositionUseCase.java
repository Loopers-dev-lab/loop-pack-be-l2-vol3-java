package com.loopers.application.queue;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.queue.WaitingQueue;

import lombok.RequiredArgsConstructor;

/**
 * 사용자의 대기열 순번과 예상 대기 시간을 조회합니다.
 *
 * <p>대기열에 진입하지 않은 사용자가 조회하면 예외를 발생시킵니다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class ReadQueuePositionUseCase {

    private static final long ESTIMATED_SECONDS_PER_USER = 5;

    private final WaitingQueue waitingQueue;

    /**
     * @param userId 조회할 사용자 ID
     * @return 순번, 전체 대기 인원, 예상 대기 시간
     * @throws CoreException 대기열에 진입하지 않은 경우 ({@code QUEUE_NOT_ENTERED})
     */
    public QueuePositionResult execute(Long userId) {
        Long rank = waitingQueue.getPosition(userId);
        if (rank == null) {
            throw new CoreException(ErrorType.QUEUE_NOT_ENTERED);
        }

        long position = rank + 1;
        long totalWaiting = waitingQueue.getTotalCount();
        long estimatedWaitSeconds = position * ESTIMATED_SECONDS_PER_USER;

        return new QueuePositionResult(position, totalWaiting, estimatedWaitSeconds);
    }
}
