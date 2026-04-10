package com.loopers.domain.outbox;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository {
    OutboxEventModel save(OutboxEventModel model);
    List<OutboxEventModel> findPendingEvents(int limit);
    int deletePublishedOlderThan(LocalDateTime cutoff, int batchSize);
    int deleteDeadOlderThan(LocalDateTime cutoff, int batchSize);
}
