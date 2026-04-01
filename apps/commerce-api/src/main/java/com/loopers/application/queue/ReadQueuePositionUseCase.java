package com.loopers.application.queue;

import java.util.Optional;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.support.entry.EntryTokenStore;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.queue.WaitingQueue;

import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class ReadQueuePositionUseCase {

    private static final long ESTIMATED_SECONDS_PER_USER = 5;

    private final WaitingQueue waitingQueue;
    private final EntryTokenStore entryTokenStore;

    public QueuePositionResult execute(Long userId) {
        Long rank = waitingQueue.getPosition(userId);
        if (rank == null) {
            Optional<String> token = entryTokenStore.getToken(userId);
            if (token.isPresent()) {
                return new QueuePositionResult(0, 0, 0, token.get());
            }
            throw new CoreException(ErrorType.QUEUE_NOT_ENTERED);
        }

        long position = rank + 1;
        long totalWaiting = waitingQueue.getTotalCount();
        long estimatedWaitSeconds = position * ESTIMATED_SECONDS_PER_USER;

        return new QueuePositionResult(position, totalWaiting, estimatedWaitSeconds, null);
    }
}
