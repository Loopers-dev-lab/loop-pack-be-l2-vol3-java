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
    private final ThroughputTracker throughputTracker;

    public QueueInfo enterQueue(Long memberId) {
        Optional<String> existingToken = entryTokenService.findToken(memberId);
        if (existingToken.isPresent()) {
            return new QueueInfo(QueueStatus.TOKEN_ISSUED, 0, 0, waitingQueueService.getTotalCount(), existingToken.get());
        }

        waitingQueueService.enter(memberId);

        Optional<Long> position = waitingQueueService.getPosition(memberId);
        long pos = position.orElse(0L);
        long totalInQueue = waitingQueueService.getTotalCount();

        long estA = throughputTracker.estimateWaitA(pos);
        long estB = throughputTracker.estimateWaitB(pos);
        long estC = throughputTracker.estimateWaitC(pos);

        return new QueueInfo(QueueStatus.WAITING, pos, estB, totalInQueue, null, estA, estB, estC);
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
        long totalInQueue = waitingQueueService.getTotalCount();

        long estA = throughputTracker.estimateWaitA(pos);
        long estB = throughputTracker.estimateWaitB(pos);
        long estC = throughputTracker.estimateWaitC(pos);

        return new QueueInfo(QueueStatus.WAITING, pos, estB, totalInQueue, null, estA, estB, estC);
    }

    public void validateToken(Long memberId, String token) {
        entryTokenService.validate(memberId, token);
    }

    public void consumeToken(Long memberId) {
        entryTokenService.consume(memberId);
    }
}
