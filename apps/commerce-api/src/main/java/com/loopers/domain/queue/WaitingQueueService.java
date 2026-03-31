package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

@Component
public class WaitingQueueService {

    private final WaitingQueueRepository waitingQueueRepository;

    public WaitingQueueService(WaitingQueueRepository waitingQueueRepository) {
        this.waitingQueueRepository = waitingQueueRepository;
    }

    public JoinQueueResult joinQueue(String eventId, Long userId, long score) {
        waitingQueueRepository.addIfAbsent(eventId, userId, score);

        Long rank = waitingQueueRepository.findRank(eventId, userId)
            .orElseThrow(() -> new CoreException(ErrorType.INTERNAL_ERROR, "대기열 순번 조회에 실패했습니다."));
        long totalWaiting = waitingQueueRepository.countWaiting(eventId);

        return new JoinQueueResult(rank, totalWaiting);
    }

    public record JoinQueueResult(
        long position,
        long totalWaiting
    ) {
    }
}

