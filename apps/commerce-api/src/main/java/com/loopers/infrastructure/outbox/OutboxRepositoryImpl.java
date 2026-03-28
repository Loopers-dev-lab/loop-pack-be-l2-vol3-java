package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Repository
public class OutboxRepositoryImpl implements OutboxRepository {

    private final OutboxJpaRepository outboxJpaRepository;

    @Override
    public OutboxEvent save(OutboxEvent event) {
        return outboxJpaRepository.save(event);
    }

    @Override
    public List<OutboxEvent> findPendingEventsForUpdate(int batchSize) {
        return outboxJpaRepository.findPendingEventsForUpdate(batchSize);
    }

    @Override
    public List<OutboxEvent> findStalledProcessingEvents(ZonedDateTime threshold) {
        return outboxJpaRepository.findStalledProcessingEvents(threshold);
    }

    @Override
    public List<OutboxEvent> findRetryableFailedEvents(ZonedDateTime threshold, int maxRetryCount) {
        return outboxJpaRepository.findRetryableFailedEvents(threshold, maxRetryCount);
    }

    @Override
    public void deletePublishedEventsBefore(ZonedDateTime threshold) {
        outboxJpaRepository.deletePublishedEventsBefore(threshold);
    }
}
