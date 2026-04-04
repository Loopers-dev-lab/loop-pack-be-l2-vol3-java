package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public class QueueDomainService {

    private final WaitingQueueRepository waitingQueueRepository;
    private final EntryTokenRepository entryTokenRepository;

    public QueueDomainService(WaitingQueueRepository waitingQueueRepository,
                              EntryTokenRepository entryTokenRepository) {
        this.waitingQueueRepository = waitingQueueRepository;
        this.entryTokenRepository = entryTokenRepository;
    }

    public QueuePosition enter(Long userId, int maxQueueSize, int tps) {
        boolean success = waitingQueueRepository.addIfNotFull(userId, System.currentTimeMillis(), maxQueueSize);
        if (!success) {
            throw new CoreException(ErrorType.SERVICE_UNAVAILABLE, "대기열이 가득 찼습니다.");
        }

        Long rank = waitingQueueRepository.rank(userId);
        if (rank == null) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "대기열 진입에 실패했습니다.");
        }

        return QueuePosition.of(rank, tps);
    }

    public QueuePollingResult getPollingResult(Long userId, int tps) {
        Long rank = waitingQueueRepository.rank(userId);
        if (rank != null) {
            return QueuePollingResult.waiting(QueuePosition.of(rank, tps));
        }

        var token = entryTokenRepository.findByUserId(userId);
        if (token.isPresent()) {
            return QueuePollingResult.tokenIssued(token.get());
        }

        var status = entryTokenRepository.getStatus(userId);
        if (status.isPresent() && QueueStatus.TOKEN_ISSUED.name().equals(status.get())) {
            return QueuePollingResult.tokenExpired();
        }

        return QueuePollingResult.notInQueue();
    }
}
