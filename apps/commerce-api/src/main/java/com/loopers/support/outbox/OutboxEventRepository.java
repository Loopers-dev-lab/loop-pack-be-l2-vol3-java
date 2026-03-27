package com.loopers.support.outbox;

import java.time.ZonedDateTime;
import java.util.List;

public interface OutboxEventRepository {

    OutboxEvent save(OutboxEvent outboxEvent);

    List<OutboxEvent> findPending(int limit);

    List<OutboxEvent> findStalePending(long staleMinutes, int limit);

    List<OutboxEvent> findRetryableEvents(int limit);

    void markPublishedByEventId(String eventId);

    void deleteSentBefore(ZonedDateTime before);
}
