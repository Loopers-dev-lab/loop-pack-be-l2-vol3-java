package com.loopers.domain.outbox;

import java.time.ZonedDateTime;
import java.util.List;

public interface OutboxEventRepository {
    OutboxEvent save(OutboxEvent outboxEvent);

    List<OutboxEvent> findPublishCandidates(ZonedDateTime now, int limit);

    boolean markProcessing(Long id, ZonedDateTime nextAttemptAt);

    void markPublished(Long id, ZonedDateTime publishedAt);

    void markAckedByEventId(java.util.UUID eventId, ZonedDateTime ackedAt);

    void markFailed(Long id, String reason, ZonedDateTime nextAttemptAt);
}
