package com.loopers.domain.idempotency;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface EventHandledRepository {
    boolean existsById(Long eventId);
    EventHandledModel save(EventHandledModel model);
    int deleteOlderThan(LocalDateTime cutoff, int batchSize);
    Set<Long> findExistingIds(List<Long> eventIds);
}
