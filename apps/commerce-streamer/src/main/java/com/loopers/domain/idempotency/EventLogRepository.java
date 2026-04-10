package com.loopers.domain.idempotency;

import java.time.LocalDateTime;

public interface EventLogRepository {
    EventLogModel save(EventLogModel model);
    int deleteOlderThan(LocalDateTime cutoff, int batchSize);
}
