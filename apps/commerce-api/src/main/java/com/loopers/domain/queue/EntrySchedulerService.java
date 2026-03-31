package com.loopers.domain.queue;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class EntrySchedulerService {

    private final WaitingQueueRepository waitingQueueRepository;
    private final EntryTokenRepository entryTokenRepository;
    private final SchedulerLockRepository schedulerLockRepository;
    private final EntryTokenGenerator entryTokenGenerator;
    private final JitterDelay jitterDelay;

    public EntrySchedulerService(
        WaitingQueueRepository waitingQueueRepository,
        EntryTokenRepository entryTokenRepository,
        SchedulerLockRepository schedulerLockRepository,
        EntryTokenGenerator entryTokenGenerator,
        JitterDelay jitterDelay
    ) {
        this.waitingQueueRepository = waitingQueueRepository;
        this.entryTokenRepository = entryTokenRepository;
        this.schedulerLockRepository = schedulerLockRepository;
        this.entryTokenGenerator = entryTokenGenerator;
        this.jitterDelay = jitterDelay;
    }

    public ReleaseResult releaseEntries(
        String eventId,
        int maxBatchSize,
        long tokenTtlSeconds,
        long lockTtlSeconds,
        String lockKey,
        String heartbeatKey,
        long heartbeatTtlSeconds
    ) {
        String lockValue = UUID.randomUUID().toString();
        boolean lockAcquired = schedulerLockRepository.tryAcquireLock(lockKey, lockValue, lockTtlSeconds);
        if (!lockAcquired) {
            return new ReleaseResult(false, 0);
        }

        List<Long> userIds = waitingQueueRepository.popOldest(eventId, maxBatchSize);
        for (Long userId : userIds) {
            long jitterMillis = ThreadLocalRandom.current().nextLong(0, 301);
            jitterDelay.delay(jitterMillis);
            String token = entryTokenGenerator.generate();
            entryTokenRepository.saveEntryToken(userId, token, tokenTtlSeconds);
        }
        schedulerLockRepository.updateHeartbeat(heartbeatKey, String.valueOf(System.currentTimeMillis()), heartbeatTtlSeconds);

        return new ReleaseResult(true, userIds.size());
    }

    public record ReleaseResult(
        boolean lockAcquired,
        int releasedCount
    ) {
    }
}

