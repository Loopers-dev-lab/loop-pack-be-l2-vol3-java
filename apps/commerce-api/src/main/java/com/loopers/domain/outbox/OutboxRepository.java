package com.loopers.domain.outbox;

import java.time.ZonedDateTime;
import java.util.List;

public interface OutboxRepository {

    OutboxEvent save(OutboxEvent event);

    List<OutboxEvent> findPendingEventsForUpdate(int batchSize);

    List<OutboxEvent> findStalledProcessingEvents(ZonedDateTime threshold);

    List<OutboxEvent> findRetryableFailedEvents(ZonedDateTime threshold, int maxRetryCount);

    void deletePublishedEventsBefore(ZonedDateTime threshold);
}
