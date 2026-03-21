package com.loopers.domain.outbox;

import java.util.List;

public interface OutboxRepository {
    OutboxModel save(OutboxModel outbox);
    List<OutboxModel> findPendingWithLimit(int limit);
}
