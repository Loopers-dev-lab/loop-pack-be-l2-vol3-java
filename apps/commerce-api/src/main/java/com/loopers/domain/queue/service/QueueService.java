package com.loopers.domain.queue.service;

import com.loopers.domain.queue.repository.QueueRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@Component
public class QueueService {

    private final QueueRepository queueRepository;

    private static final int BATCH_SIZE = 50;
    private static final double SCHEDULER_INTERVAL_SEC = 0.5;

    @CircuitBreaker(name = "redis-queue", fallbackMethod = "fallbackEnter")
    public QueuePositionInfo enter(Long memberId) {
        if (queueRepository.getScore(memberId) != null) {
            return getPosition(memberId);
        }

        long score = queueRepository.generateSequence();
        queueRepository.addToQueue(memberId, score);

        Long rank = queueRepository.getRank(memberId);
        Long total = queueRepository.getTotalWaiting();

        return buildPositionInfo(rank, total);
    }

    @CircuitBreaker(name = "redis-queue", fallbackMethod = "fallbackGetPosition")
    public QueuePositionInfo getPosition(Long memberId) {
        Long rank = queueRepository.getRank(memberId);
        if (rank == null) {
            throw new CoreException(ErrorType.NOT_FOUND, "대기열에 등록되어 있지 않습니다.");
        }
        Long total = queueRepository.getTotalWaiting();

        return buildPositionInfo(rank, total);
    }

    @CircuitBreaker(name = "redis-queue", fallbackMethod = "fallbackEnterWithLua")
    public QueuePositionInfo enterWithLua(Long memberId) {
        var result = queueRepository.enterQueue(memberId);
        return buildPositionInfo(result.rank(), result.total());
    }

    public QueuePositionInfo fallbackEnterWithLua(Long memberId, Exception e) {
        log.warn("Redis 장애, 대기열 진입 차단 - memberId: {}", memberId, e);
        throw new CoreException(ErrorType.INTERNAL_ERROR, "현재 대기열 서비스를 이용할 수 없습니다.");
    }

    public void removeFromQueue(Long memberId) {
        queueRepository.removeFromQueue(memberId);
    }

    public Set<String> getTopMembers(int count) {
        return queueRepository.getTopMembers(count);
    }

    public QueuePositionInfo fallbackEnter(Long memberId, Exception e) {
        log.warn("Redis 장애, 대기열 진입 차단 - memberId: {}", memberId, e);
        throw new CoreException(ErrorType.INTERNAL_ERROR, "현재 대기열 서비스를 이용할 수 없습니다.");
    }

    public QueuePositionInfo fallbackGetPosition(Long memberId, Exception e) {
        log.warn("Redis 장애, 순번 조회 불가 - memberId: {}", memberId, e);
        throw new CoreException(ErrorType.INTERNAL_ERROR, "현재 대기열 서비스를 이용할 수 없습니다.");
    }

    private QueuePositionInfo buildPositionInfo(Long rank, Long total) {
        int estimatedWait = calculateEstimatedWait(rank);
        long nextPoll = calculateNextPollMs(rank);
        return new QueuePositionInfo(rank, total, estimatedWait, nextPoll);
    }

    private int calculateEstimatedWait(Long rank) {
        if (rank == null) return 0;
        double throughputPerSec = BATCH_SIZE / SCHEDULER_INTERVAL_SEC;
        return (int) Math.ceil(rank / throughputPerSec);
    }

    private long calculateNextPollMs(Long rank) {
        if (rank == null) return 1000;
        if (rank <= 10) return 1000;
        if (rank <= 50) return 3000;
        if (rank <= 200) return 5000;
        return 10000;
    }

    public record QueuePositionInfo(
            Long rank,
            Long totalWaiting,
            int estimatedWaitSeconds,
            long nextPollAfterMs
    ) {}
}
