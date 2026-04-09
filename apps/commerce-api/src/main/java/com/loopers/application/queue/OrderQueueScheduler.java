package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.WaitingQueueRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class OrderQueueScheduler {

    private static final String LOCK_KEY = "lock:queue-scheduler";
    private static final long LOCK_TTL_MS = 5000;

    private final WaitingQueueRepository waitingQueueRepository;
    private final EntryTokenRepository entryTokenRepository;
    private final OrderQueueReader orderQueueReader;
    private final int batchSize;
    private final int maxSlot;
    private final Duration tokenTtl;

    public OrderQueueScheduler(
            WaitingQueueRepository waitingQueueRepository,
            EntryTokenRepository entryTokenRepository,
            OrderQueueReader orderQueueReader,
            @Value("${queue.batch-size:14}") int batchSize,
            @Value("${queue.max-slot:140}") int maxSlot,
            @Value("${queue.token-ttl:PT5M}") Duration tokenTtl
    ) {
        this.waitingQueueRepository = waitingQueueRepository;
        this.entryTokenRepository = entryTokenRepository;
        this.orderQueueReader = orderQueueReader;
        this.batchSize = batchSize;
        this.maxSlot = maxSlot;
        this.tokenTtl = tokenTtl;
    }

    @Scheduled(fixedRateString = "${queue.scheduler.interval-ms:100}", initialDelay = 1000)
    public void issueTokens() {
        if (!orderQueueReader.isEnabled()) {
            return;
        }

        Optional<String> lockValue = entryTokenRepository.acquireLock(LOCK_KEY, LOCK_TTL_MS);
        if (lockValue.isEmpty()) {
            return;
        }

        try {
            issueBatch();
        } finally {
            entryTokenRepository.releaseLock(LOCK_KEY, lockValue.get());
        }
    }

    public void issueBatch() {
        long activeTokens = entryTokenRepository.countActiveTokens();
        int availableSlots = (int) (maxSlot - activeTokens);
        if (availableSlots <= 0) {
            return;
        }

        int actualBatchSize = Math.min(availableSlots, batchSize);
        Set<Long> userIds = waitingQueueRepository.dequeue(actualBatchSize);
        for (Long userId : userIds) {
            String token = UUID.randomUUID().toString();
            entryTokenRepository.issueToken(userId, token, tokenTtl);
        }
    }
}
