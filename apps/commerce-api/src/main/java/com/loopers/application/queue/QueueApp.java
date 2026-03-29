package com.loopers.application.queue;

import com.loopers.config.QueueProperties;
import com.loopers.domain.queue.EntryTokenService;
import com.loopers.domain.queue.WaitingQueueService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class QueueApp {

    private final WaitingQueueService waitingQueueService;
    private final EntryTokenService entryTokenService;
    private final QueueProperties queueProperties;

    public QueueInfo enterQueue(Long memberId) {
        Optional<String> existingToken = entryTokenService.findToken(memberId);
        if (existingToken.isPresent()) {
            return new QueueInfo(QueueStatus.TOKEN_ISSUED, 0, 0, waitingQueueService.getTotalCount(), existingToken.get());
        }

        waitingQueueService.enter(memberId);

        Optional<Long> position = waitingQueueService.getPosition(memberId);
        long pos = position.orElse(0L);
        long estimatedWaitSeconds = calculateEstimatedWaitSeconds(pos);
        long totalInQueue = waitingQueueService.getTotalCount();

        return new QueueInfo(QueueStatus.WAITING, pos, estimatedWaitSeconds, totalInQueue, null);
    }

    public QueueInfo getQueueStatus(Long memberId) {
        Optional<String> token = entryTokenService.findToken(memberId);
        if (token.isPresent()) {
            return new QueueInfo(QueueStatus.TOKEN_ISSUED, 0, 0, waitingQueueService.getTotalCount(), token.get());
        }

        Optional<Long> position = waitingQueueService.getPosition(memberId);
        if (position.isEmpty()) {
            throw new CoreException(ErrorType.QUEUE_NOT_FOUND);
        }

        long pos = position.get();
        long estimatedWaitSeconds = calculateEstimatedWaitSeconds(pos);
        long totalInQueue = waitingQueueService.getTotalCount();

        return new QueueInfo(QueueStatus.WAITING, pos, estimatedWaitSeconds, totalInQueue, null);
    }

    private long calculateEstimatedWaitSeconds(long position) {
        if (position <= 0) {
            return 0;
        }
        return (long) Math.ceil((double) position / queueProperties.throughputPerSecond());
    }
}
