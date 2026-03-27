package com.loopers.support.outbox;

import java.time.ZonedDateTime;
import java.util.List;

public interface OutboxEventRepository {

    OutboxEvent save(OutboxEvent outboxEvent);

    List<OutboxEvent> findPending(int limit);

    void deleteSentBefore(ZonedDateTime before);
}
