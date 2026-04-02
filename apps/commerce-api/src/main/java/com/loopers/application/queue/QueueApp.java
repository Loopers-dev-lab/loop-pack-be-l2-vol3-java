package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenService;
import com.loopers.domain.queue.QueueMode;
import com.loopers.domain.queue.QueueModeRepository;
import com.loopers.domain.queue.WaitingQueueService;
import com.loopers.infrastructure.queue.QueueStatusLuaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueApp {

    private static final String CB_NAME = "queue-redis";
    private static final String SCHEDULER_NAME = "queue-scheduler";

    private final WaitingQueueService waitingQueueService;
    private final EntryTokenService entryTokenService;
    private final ThroughputTracker throughputTracker;
    private final QueueModeRepository queueModeRepository;
    private final QueueStatusLuaRepository queueStatusLuaRepository;

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "enterQueueFallback")
    public QueueInfo enterQueue(Long memberId) {
        QueueMode mode = queueModeRepository.getCurrentMode();

        if (mode == QueueMode.CLOSED) {
            throw new CoreException(ErrorType.QUEUE_FULL);
        }

        if (mode == QueueMode.BYPASS) {
            String token = entryTokenService.issue(memberId);
            return new QueueInfo(QueueStatus.TOKEN_ISSUED, 0, 0, 0, token);
        }

        Optional<String> existingToken = entryTokenService.findToken(memberId);
        if (existingToken.isPresent()) {
            return new QueueInfo(QueueStatus.TOKEN_ISSUED, 0, 0, waitingQueueService.getTotalCount(), existingToken.get());
        }

        waitingQueueService.enter(memberId);

        Optional<Long> position = waitingQueueService.getPosition(memberId);
        long pos = position.orElse(0L);
        long totalInQueue = waitingQueueService.getTotalCount();
        long estimatedWait = throughputTracker.estimateWait(pos);

        return new QueueInfo(QueueStatus.WAITING, pos, estimatedWait, totalInQueue, null, true);
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "getQueueStatusFallback")
    public QueueInfo getQueueStatus(Long memberId) {
        QueueStatusLuaRepository.QueueStatusResult result = queueStatusLuaRepository.getStatus(memberId, SCHEDULER_NAME);

        if ("TOKEN".equals(result.status())) {
            return new QueueInfo(QueueStatus.TOKEN_ISSUED, 0, 0, result.totalInQueue(), result.token(), result.healthy());
        }
        if ("NOT_FOUND".equals(result.status())) {
            throw new CoreException(ErrorType.QUEUE_NOT_FOUND);
        }

        long estimatedWait = throughputTracker.estimateWait(result.position());
        return new QueueInfo(QueueStatus.WAITING, result.position(), estimatedWait, result.totalInQueue(), null, result.healthy());
    }

    public void resetPosition(Long memberId) {
        waitingQueueService.reEnter(memberId);
        log.warn("[QUEUE_ABUSE] 순번 리셋 — 폴링 어뷰징 감지. memberId={}", memberId);
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "validateTokenFallback")
    public void validateToken(Long memberId, String token) {
        entryTokenService.validate(memberId, token);
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "consumeTokenFallback")
    public void consumeToken(Long memberId) {
        entryTokenService.consume(memberId);
    }

    private QueueInfo enterQueueFallback(Long memberId, Throwable t) {
        log.warn("[QUEUE_CB] enterQueue circuit open — Redis 장애로 대기열 차단. memberId={}", memberId, t);
        throw new CoreException(ErrorType.QUEUE_FULL);
    }

    private QueueInfo getQueueStatusFallback(Long memberId, Throwable t) {
        log.warn("[QUEUE_CB] getQueueStatus circuit open — Redis 장애로 대기열 차단. memberId={}", memberId, t);
        throw new CoreException(ErrorType.QUEUE_FULL);
    }

    private void validateTokenFallback(Long memberId, String token, Throwable t) {
        log.warn("[QUEUE_CB] validateToken circuit open — Redis 장애로 주문 차단. memberId={}", memberId, t);
        throw new CoreException(ErrorType.QUEUE_FULL);
    }

    private void consumeTokenFallback(Long memberId, Throwable t) {
        log.warn("[QUEUE_CB] consumeToken circuit open — Redis 장애로 토큰 소비 실패 (무시). memberId={}", memberId);
    }
}
