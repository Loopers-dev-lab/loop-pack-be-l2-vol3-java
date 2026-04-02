package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.EntryToken;
import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.QueueStatus;
import com.loopers.domain.queue.WaitingQueueRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@ConditionalOnProperty(name = "queue.scheduler.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
@Component
public class QueueScheduler {

    private static final String LOCK_KEY = "lock:scheduler";

    private final WaitingQueueRepository waitingQueueRepository;
    private final EntryTokenRepository entryTokenRepository;
    private final int maxSlot;
    private final int batchSize;
    private final long schedulerLockTtlMs;
    private final long jitterRangeMs;
    private final long tokenTtlSeconds;
    private final long statusTtlSeconds;

    public QueueScheduler(
        WaitingQueueRepository waitingQueueRepository,
        EntryTokenRepository entryTokenRepository,
        @Value("${queue.max-slot}") int maxSlot,
        @Value("${queue.batch-size}") int batchSize,
        @Value("${queue.scheduler-lock-ttl-ms}") long schedulerLockTtlMs,
        @Value("${queue.jitter-range-ms}") long jitterRangeMs,
        @Value("${queue.token-ttl-seconds}") long tokenTtlSeconds,
        @Value("${queue.status-ttl-seconds}") long statusTtlSeconds
    ) {
        this.waitingQueueRepository = waitingQueueRepository;
        this.entryTokenRepository = entryTokenRepository;
        this.maxSlot = maxSlot;
        this.batchSize = batchSize;
        this.schedulerLockTtlMs = schedulerLockTtlMs;
        this.jitterRangeMs = jitterRangeMs;
        this.tokenTtlSeconds = tokenTtlSeconds;
        this.statusTtlSeconds = statusTtlSeconds;
    }

    @Scheduled(fixedDelayString = "${queue.scheduler.interval-ms:100}")
    public void processQueue() {
        Optional<String> lockValue = entryTokenRepository.acquireLock(LOCK_KEY, schedulerLockTtlMs);
        if (lockValue.isEmpty()) {
            return;
        }
        try {
            recoverOrphanedBatch();

            long activeTokens = entryTokenRepository.countActiveTokens();
            int availableSlots = (int) (maxSlot - activeTokens);
            if (availableSlots <= 0) {
                return;
            }

            int actualBatchSize = Math.min(availableSlots, batchSize);
            List<Long> userIds = waitingQueueRepository.popMin(actualBatchSize);

            if (userIds.isEmpty()) {
                return;
            }

            entryTokenRepository.saveStagingBatch(userIds);

            long now = System.currentTimeMillis();
            for (Long userId : userIds) {
                String token = UUID.randomUUID().toString();
                long jitter = jitterRangeMs > 0 ? ThreadLocalRandom.current().nextLong(0, jitterRangeMs) : 0;
                long activateAt = now + jitter;
                EntryToken entryToken = new EntryToken(userId, token, activateAt);

                entryTokenRepository.save(entryToken, tokenTtlSeconds);
                entryTokenRepository.saveStatus(userId, QueueStatus.TOKEN_ISSUED.name(), statusTtlSeconds);
            }

            entryTokenRepository.deleteStagingBatch();
            log.info("[Queue Scheduler] {}명에게 입장 토큰 발급 완료 (활성 토큰: {})", userIds.size(), activeTokens + userIds.size());
        } finally {
            entryTokenRepository.releaseLock(LOCK_KEY, lockValue.get());
        }
    }

    private void recoverOrphanedBatch() {
        List<Long> orphaned = entryTokenRepository.getStagingBatch();
        if (orphaned.isEmpty()) {
            return;
        }

        for (Long userId : orphaned) {
            if (entryTokenRepository.findByUserId(userId).isEmpty()) {
                waitingQueueRepository.add(userId, System.currentTimeMillis());
            }
        }
        entryTokenRepository.deleteStagingBatch();
        log.warn("[Queue Scheduler] {}명의 고아 사용자를 대기열에 복구", orphaned.size());
    }
}
