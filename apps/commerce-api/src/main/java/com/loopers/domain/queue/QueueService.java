package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.QueueErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 대기열 Domain Service
 *
 * 대기열 진입, 순번 조회, 토큰 발급/검증/삭제를 처리한다.
 * Blue Book 스타일: Domain Service가 Repository를 직접 호출한다.
 */
@Component
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    private final QueueRepository queueRepository;
    private final QueueProperties queueProperties;

    public QueueService(QueueRepository queueRepository, QueueProperties queueProperties) {
        this.queueRepository = queueRepository;
        this.queueProperties = queueProperties;
    }

    /**
     * 대기열에 진입한다.
     * 이미 활성 토큰이 있으면 즉시 활성 상태를 반환한다.
     * 이미 대기 중이면 현재 순번을 반환한다 (ZADD NX — 멱등).
     */
    public QueueEntryResult enter(Long userId) {
        if (queueRepository.hasValidToken(userId)) {
            return QueueEntryResult.alreadyActive();
        }

        long currentSize = queueRepository.getTotalWaiting();
        if (currentSize >= queueProperties.getMaxWaitingSize()) {
            throw new CoreException(QueueErrorType.QUEUE_FULL);
        }

        double score = System.currentTimeMillis();
        boolean added = queueRepository.addToWaitingQueue(userId, score);

        if (added) {
            log.info("대기열 진입: userId={}", userId);
        }

        return buildWaitingResult(userId);
    }

    /**
     * 현재 대기 상태를 조회한다.
     * 활성 토큰이 있으면 ACTIVE, 대기 중이면 WAITING, 둘 다 아니면 NOT_IN_QUEUE.
     */
    public QueuePositionResult getPosition(Long userId) {
        if (queueRepository.hasValidToken(userId)) {
            long ttl = queueRepository.getTokenTtl(userId);
            return QueuePositionResult.active(Math.max(ttl, 0));
        }

        Optional<Long> position = queueRepository.getPosition(userId);
        if (position.isEmpty()) {
            return QueuePositionResult.notInQueue();
        }

        long rank = position.get();
        long totalWaiting = queueRepository.getTotalWaiting();
        int estimatedWaitSec = calculateEstimatedWait(rank + 1);
        int nextPollMs = calculateNextPollInterval(rank + 1);

        return QueuePositionResult.waiting(rank + 1, totalWaiting, estimatedWaitSec, nextPollMs);
    }

    /**
     * 스케줄러가 호출: 대기열 앞쪽에서 배치 크기만큼 활성화한다.
     */
    public int activateNextBatch() {
        int batchSize = queueProperties.getBatchSize();
        int tokenTtlSeconds = queueProperties.getTokenTtlSeconds();

        List<Long> activated = queueRepository.activateFromQueue(batchSize, tokenTtlSeconds);

        if (!activated.isEmpty()) {
            log.info("대기열 활성화: {}명 (배치 크기={})", activated.size(), batchSize);
        }

        return activated.size();
    }

    /**
     * 입장 토큰이 유효한지 검증한다.
     */
    public boolean hasValidToken(Long userId) {
        return queueRepository.hasValidToken(userId);
    }

    /**
     * 입장 토큰을 삭제한다 (주문 완료 후).
     */
    public void deleteToken(Long userId) {
        queueRepository.deleteToken(userId);
        log.info("토큰 삭제: userId={}", userId);
    }

    /**
     * 대기열에서 나간다 (사용자 취소).
     */
    public void leave(Long userId) {
        queueRepository.removeFromWaitingQueue(userId);
        log.info("대기열 이탈: userId={}", userId);
    }

    /**
     * 예상 대기 시간을 계산한다.
     * position / (batchSize / schedulerIntervalSec)
     */
    int calculateEstimatedWait(long position) {
        double throughputPerSec = (double) queueProperties.getBatchSize()
                / queueProperties.getSchedulerIntervalMs() * 1000;
        if (throughputPerSec <= 0) {
            return 0;
        }
        return (int) Math.ceil(position / throughputPerSec);
    }

    /**
     * 동적 폴링 주기를 계산한다.
     * 앞쪽 사용자: 짧은 주기 (1초), 뒤쪽 사용자: 긴 주기 (10초)
     */
    int calculateNextPollInterval(long position) {
        if (position <= 50) return 1000;
        if (position <= 500) return 3000;
        if (position <= 5000) return 5000;
        return 10000;
    }

    private QueueEntryResult buildWaitingResult(Long userId) {
        Optional<Long> position = queueRepository.getPosition(userId);
        if (position.isEmpty()) {
            return QueueEntryResult.alreadyActive();
        }

        long rank = position.get();
        long totalWaiting = queueRepository.getTotalWaiting();
        int estimatedWaitSec = calculateEstimatedWait(rank + 1);

        return QueueEntryResult.waiting(rank + 1, totalWaiting, estimatedWaitSec);
    }
}
