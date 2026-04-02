package com.loopers.application.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QueueAppService {

    private final QueueService queueService;
    private final TokenService tokenService;

    @Value("${queue.scheduler.batch-size:14}")
    private int batchSize;

    @Value("${queue.scheduler.fixed-rate:100}")
    private long fixedRateMs;

    @Value("${queue.max-size:10000}")
    private long maxSize;

    private long tps() {
        return batchSize * (1000L / fixedRateMs);
    }

    @Transactional(readOnly = true)
    public long enter(Long userId) {
        if (queueService.getSize() >= maxSize) {
            throw new CoreException(ErrorType.QUEUE_FULL);
        }
        long rank = queueService.enter(userId);
        return rank + 1;
    }

    @Transactional(readOnly = true)
    public QueuePositionResult getPosition(Long userId) {
        // 토큰 먼저 확인 — 큐에 있더라도 토큰이 있으면 입장 가능 상태
        if (tokenService.validate(userId)) {
            return new QueuePositionResult(0, 0, true, 0L);
        }
        try {
            long rank = queueService.getRank(userId);
            long position = rank + 1;
            long estimatedWaitSeconds = (long) Math.ceil((double) rank / tps());
            long nextPollIntervalMs = calcNextPollInterval(position, estimatedWaitSeconds);
            return new QueuePositionResult(position, estimatedWaitSeconds, false, nextPollIntervalMs);
        } catch (CoreException e) {
            if (e.getErrorType() == ErrorType.QUEUE_NOT_FOUND) {
                return new QueuePositionResult(0, 0, false, 500L);
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public long getSize() {
        return queueService.getSize();
    }

    private long calcNextPollInterval(long position, long estimatedWaitSeconds) {
        if (position <= batchSize) {
            return 500L;
        }
        return Math.min(estimatedWaitSeconds * 500L, 5000L);
    }

    public record QueuePositionResult(long position, long estimatedWaitSeconds, boolean tokenIssued, long nextPollIntervalMs) {}
}
