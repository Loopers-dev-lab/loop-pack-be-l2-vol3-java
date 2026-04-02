package com.loopers.domain.outbox;

import java.util.List;

/**
 * OutboxEvent Repository 인터페이스.
 */
public interface OutboxEventRepository {

    OutboxEvent save(OutboxEvent event);

    List<OutboxEvent> findPendingEvents(int limit);

    void updateStatus(Long id, OutboxStatus status);

    void markAsSent(Long id);

    void incrementRetryCount(Long id);
}
