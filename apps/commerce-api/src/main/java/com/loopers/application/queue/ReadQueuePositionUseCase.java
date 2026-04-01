package com.loopers.application.queue;

import java.util.Objects;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.support.entry.EntryTokenStore;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.queue.WaitingQueue;

import lombok.RequiredArgsConstructor;

/**
 * 대기열 순번 및 예상 대기 시간을 조회합니다.
 */
@UseCase
@RequiredArgsConstructor
public class ReadQueuePositionUseCase {

    private static final long THROUGHPUT_PER_SECOND = 60;

    private final WaitingQueue waitingQueue;
    private final EntryTokenStore entryTokenStore;

    /**
     * @param userId 대기열 순번을 조회할 사용자 ID
     * @return 순번, 총 대기 인원, 예상 대기 시간(초), 입장 토큰(입장 허용된 경우)
     * @throws CoreException 대기열에 진입하지 않은 경우 ({@code QUEUE_NOT_ENTERED})
     */
    public QueuePositionResult execute(Long userId) {
        Long rank = waitingQueue.getPosition(userId);
        if (Objects.nonNull(rank)) {
            long position = rank + 1;
            long totalWaiting = waitingQueue.getTotalCount();
            long estimatedWaitSeconds = (long) Math.ceil((double) position / THROUGHPUT_PER_SECOND);
            return new QueuePositionResult(position, totalWaiting, estimatedWaitSeconds, null);
        }

        return entryTokenStore.getToken(userId)
                .map(QueuePositionResult::admitted)
                .orElseThrow(() -> new CoreException(ErrorType.QUEUE_NOT_ENTERED));
    }
}
