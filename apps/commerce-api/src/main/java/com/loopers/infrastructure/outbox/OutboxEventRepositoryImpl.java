package com.loopers.infrastructure.outbox;

import com.loopers.support.outbox.OutboxEvent;
import com.loopers.support.outbox.OutboxEventRepository;
import com.loopers.support.outbox.OutboxEventStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private final OutboxEventJpaRepository jpaRepository;

    @Override
    public OutboxEvent save(OutboxEvent outboxEvent) {
        return jpaRepository.save(outboxEvent);
    }

    @Override
    public List<OutboxEvent> findPending(int limit) {
        return jpaRepository.findByStatusOrderByIdAsc(OutboxEventStatus.PENDING, PageRequest.of(0, limit));
    }

    @Override
    public List<OutboxEvent> findStalePending(long staleMinutes, int limit) {
        ZonedDateTime threshold = ZonedDateTime.now().minusMinutes(staleMinutes);
        return jpaRepository.findByStatusAndCreatedAtBeforeOrderByIdAsc(
                OutboxEventStatus.PENDING, threshold, PageRequest.of(0, limit));
    }

    @Override
    public List<OutboxEvent> findRetryableEvents(int limit) {
        return jpaRepository.findRetryableEvents(PageRequest.of(0, limit));
    }

    @Override
    public void markPublishedByEventId(String eventId) {
        jpaRepository.markPublishedByEventId(eventId);
    }

    @Override
    public void deleteSentBefore(ZonedDateTime before) {
        jpaRepository.deleteByStatusAndSentAtBefore(OutboxEventStatus.SENT, before);
    }
}
