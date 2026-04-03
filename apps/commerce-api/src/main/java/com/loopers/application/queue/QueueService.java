package com.loopers.application.queue;

import com.loopers.domain.queue.QueueRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class QueueService {

    private final QueueRepository queueRepository;
    private final QueueMetrics queueMetrics;

    public QueueStatus enter(String eventId, Long userId) {
        boolean added = queueRepository.add(eventId, userId);
        if (!added) {
            throw new CoreException(ErrorType.CONFLICT, "이미 대기열에 등록된 사용자입니다.");
        }
        long totalWaiting = queueRepository.getTotalCount(eventId);
        long position = queueRepository.getPosition(eventId, userId)
                .map(rank -> rank + 1)
                .orElse(totalWaiting);
        long estimatedWait = calculateEstimatedWait(position);
        queueMetrics.recordEnter(eventId);
        return new QueueStatus(position, totalWaiting, estimatedWait);
    }

    public QueueStatus getPosition(String eventId, Long userId) {
        Optional<Long> rankOpt = queueRepository.getPosition(eventId, userId);
        long totalWaiting = queueRepository.getTotalCount(eventId);
        if (rankOpt.isEmpty()) {
            return new QueueStatus(0, totalWaiting, 0);
        }
        long position = rankOpt.get() + 1;
        long estimatedWait = calculateEstimatedWait(position);
        return new QueueStatus(position, totalWaiting, estimatedWait);
    }

    private long calculateEstimatedWait(long position) {
        if (position <= 0) return 0;
        long batchesAhead = (position - 1) / QueueConstants.BATCH_SIZE;
        return (batchesAhead + 1) * QueueConstants.SCHEDULER_INTERVAL_SECONDS;
    }

    public record QueueStatus(long position, long totalWaiting, long estimatedWaitSeconds) {}
}
