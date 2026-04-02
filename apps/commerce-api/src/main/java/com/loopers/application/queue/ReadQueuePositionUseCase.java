package com.loopers.application.queue;

import java.util.Objects;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.support.entry.EntryTokenStore;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.queue.WaitingQueue;

import lombok.RequiredArgsConstructor;

/**
 * 대기열 순번, 예상 대기 시간, 권장 폴링 주기를 조회한다.
 */
@UseCase
@RequiredArgsConstructor
public class ReadQueuePositionUseCase {

    private final WaitingQueue waitingQueue;
    private final EntryTokenStore entryTokenStore;

    /**
     * @param userId 대기열 순번을 조회할 사용자 ID
     * @return 순번, 총 대기 인원, 예상 대기 시간(초), 권장 폴링 주기(ms), 입장 토큰
     * @throws CoreException 대기열에 진입하지 않은 경우 ({@code QUEUE_NOT_ENTERED})
     */
    public QueuePositionResult execute(Long userId) {
        Long rank = waitingQueue.getPosition(userId);
        if (Objects.nonNull(rank)) {
            long totalWaiting = waitingQueue.getTotalCount();
            return QueuePositionCalculator.calculate(rank, totalWaiting);
        }

        return entryTokenStore.getToken(userId)
                .map(QueuePositionResult::admitted)
                .orElseThrow(() -> new CoreException(ErrorType.QUEUE_NOT_ENTERED));
    }
}
