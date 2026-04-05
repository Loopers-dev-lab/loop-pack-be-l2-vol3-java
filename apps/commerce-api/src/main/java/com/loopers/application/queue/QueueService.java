package com.loopers.application.queue;

import com.loopers.domain.queue.QueueRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QueueService {

    private static final int THROUGHPUT_PER_SECOND = 175;

    private final QueueRepository queueRepository;

    public QueueDto.QueueEntryResult enter(Long userId) {
        double score = System.currentTimeMillis();
        queueRepository.enterWaitQueue(userId, score);

        Long position = queueRepository.getWaitPosition(userId);
        Long totalWaiting = queueRepository.getWaitTotalCount();
        Long estimatedWaitSeconds = calculateEstimatedWait(position);
        Long nextPollAfterSeconds = calculatePollInterval(position);

        return QueueDto.QueueEntryResult.of(position, totalWaiting, estimatedWaitSeconds, nextPollAfterSeconds);
    }

    public QueueDto.QueuePositionResult getPosition(Long userId) {
        String token = queueRepository.getToken(userId);
        if (token != null) {
            Long totalWaiting = queueRepository.getWaitTotalCount();
            return QueueDto.QueuePositionResult.of(0L, totalWaiting, 0L, token, null);
        }

        Long position = queueRepository.getWaitPosition(userId);
        if (position == null) {
            throw new CoreException(ErrorType.QUEUE_NOT_ENTERED);
        }

        Long totalWaiting = queueRepository.getWaitTotalCount();
        Long estimatedWaitSeconds = calculateEstimatedWait(position);
        Long nextPollAfterSeconds = calculatePollInterval(position);

        return QueueDto.QueuePositionResult.of(position, totalWaiting, estimatedWaitSeconds, null, nextPollAfterSeconds);
    }

    private Long calculateEstimatedWait(Long position) {
        if (position == null || position <= 0) {
            return 0L;
        }
        return (long) Math.ceil((double) position / THROUGHPUT_PER_SECOND);
    }

    /**
     * 순번 구간별 Adaptive Polling 주기 (초)
     * - 1~100:       2초 (곧 입장, 빠른 피드백)
     * - 101~1,000:   5초 (수 분 대기)
     * - 1,001~10,000: 10초 (수십 분 대기)
     * - 10,001+:     30초 (1시간+ 대기, 순번 변동 체감 없음)
     */
    private Long calculatePollInterval(Long position) {
        if (position == null || position <= 100) {
            return 2L;
        }
        if (position <= 1_000) {
            return 5L;
        }
        if (position <= 10_000) {
            return 10L;
        }
        return 30L;
    }
}
