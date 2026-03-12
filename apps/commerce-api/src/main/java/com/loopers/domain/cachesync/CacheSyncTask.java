package com.loopers.domain.cachesync;

import java.time.ZonedDateTime;
import java.util.UUID;

public record CacheSyncTask(
        Long id,
        CacheSyncAggregateType aggregateType,
        UUID aggregateId,
        CacheSyncOperationType operationType,
        String payloadJson,
        CacheSyncTaskStatus status,
        int attemptCount,
        ZonedDateTime nextAttemptAt,
        String lastError
) {
    public static CacheSyncTask pending(
            CacheSyncAggregateType aggregateType,
            UUID aggregateId,
            CacheSyncOperationType operationType,
            String payloadJson,
            String lastError
    ) {
        return new CacheSyncTask(
                null,
                aggregateType,
                aggregateId,
                operationType,
                payloadJson,
                CacheSyncTaskStatus.PENDING,
                0,
                ZonedDateTime.now(),
                lastError
        );
    }
}
