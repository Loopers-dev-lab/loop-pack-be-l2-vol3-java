package com.loopers.application.queue;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.queue.WaitingQueue;

import lombok.RequiredArgsConstructor;

/**
 * 사용자가 대기열에 진입합니다.
 *
 * <p>이미 대기열에 존재하는 사용자가 재진입을 시도하면 예외를 발생시킵니다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class EnterQueueUseCase {

    private final WaitingQueue waitingQueue;

    /**
     * @param userId 대기열에 진입할 사용자 ID
     * @throws CoreException 이미 대기열에 진입한 경우 ({@code ALREADY_IN_QUEUE})
     */
    public void execute(Long userId) {
        boolean entered = waitingQueue.enter(userId);
        if (!entered) {
            throw new CoreException(ErrorType.ALREADY_IN_QUEUE);
        }
    }
}
