package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.QueuePosition;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QueueFacade {

    private final WaitingQueueRepository waitingQueueRepository;
    private final EntryTokenRepository entryTokenRepository;

    @Value("${queue.batch-size:18}")
    private int batchSize;

    @Value("${queue.scheduler-interval-ms:100}")
    private int schedulerIntervalMs;

    public QueuePosition enterQueue(Long userId) {
        double score = System.currentTimeMillis();
        waitingQueueRepository.enqueue(userId, score);
        // 신규든 재진입이든 현재 순번 반환 (ZADD NX로 score 갱신은 없음)
        return getPosition(userId);
    }

    public QueuePosition getPosition(Long userId) {
        Long rank = waitingQueueRepository.getPosition(userId); // ZRANK

        if (rank != null) {
            // 큐에 있음 — ZRANGE 방식에서는 토큰 발급 후에도 큐에 남아 있으므로 토큰 확인 필요
            if (entryTokenRepository.existsByUserId(userId)) {
                // 토큰 발급됨 → 이 시점에 ZREM으로 큐에서 제거 (폴링 API 책임)
                waitingQueueRepository.remove(userId);
                return QueuePosition.ofTokenIssued();
            }
            // 아직 토큰 미발급 → 순번 반환
            long totalCount = waitingQueueRepository.getTotalCount();
            return QueuePosition.of(rank, totalCount, batchSize, schedulerIntervalMs);
        }

        // 큐에 없음 (이미 ZREM 됐거나, 진입하지 않은 유저)
        if (entryTokenRepository.existsByUserId(userId)) {
            // 중복 폴링 등으로 이미 ZREM된 상태에서 재조회
            return QueuePosition.ofTokenIssued();
        }
        throw new CoreException(ErrorType.NOT_FOUND, "대기열에 존재하지 않는 유저입니다.");
    }
}
