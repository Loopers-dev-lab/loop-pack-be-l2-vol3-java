package com.loopers.domain.outbox;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository {
    OutboxEvent save(OutboxEvent outboxEvent);

    List<OutboxEvent> findPublishCandidates(ZonedDateTime now, int limit);

    boolean markProcessing(Long id, ZonedDateTime nextAttemptAt);

    void markPublished(Long id, ZonedDateTime publishedAt);

    void markAckedByEventId(UUID eventId, ZonedDateTime ackedAt);

    void markFailed(Long id, String reason, ZonedDateTime nextAttemptAt);
}
