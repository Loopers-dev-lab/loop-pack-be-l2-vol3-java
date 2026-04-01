package com.loopers.domain.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WaitingQueueService {

    // DB 커넥션 풀 40개 × 초당 5건(1건 200ms) = 200 TPS → 안전 마진 70% = 140 TPS
    private static final int THROUGHPUT_PER_SECOND = 140;

    private final WaitingQueueRepository waitingQueueRepository;
    private final EntryTokenRepository entryTokenRepository;

    /** 대기열 진입 */
    public QueueEntryResult enter(Long userId) {
        double score = System.currentTimeMillis();
        boolean isNew = waitingQueueRepository.enqueue(userId, score);

        Long rank = waitingQueueRepository.getRank(userId);
        long totalCount = waitingQueueRepository.getTotalCount();
        long estimatedWaitSeconds = calculateEstimatedWait(rank);

        return new QueueEntryResult(rank + 1, totalCount, estimatedWaitSeconds, isNew);
    }

    /** 순번 조회 (토큰 발급 여부 포함) */
    public QueuePositionResult getPosition(Long userId) {
        Optional<String> token = entryTokenRepository.getToken(userId);
        if (token.isPresent()) {
            return QueuePositionResult.tokenIssued(token.get());
        }

        Long rank = waitingQueueRepository.getRank(userId);
        if (rank == null) {
            return QueuePositionResult.notInQueue();
        }

        long totalCount = waitingQueueRepository.getTotalCount();
        long estimatedWaitSeconds = calculateEstimatedWait(rank);
        return QueuePositionResult.waiting(rank + 1, totalCount, estimatedWaitSeconds);
    }

    private long calculateEstimatedWait(Long rank) {
        if (rank == null) return 0;
        return (long) Math.ceil((double) rank / THROUGHPUT_PER_SECOND);
    }
}
