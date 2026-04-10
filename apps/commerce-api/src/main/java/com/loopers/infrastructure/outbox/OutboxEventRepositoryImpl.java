package com.loopers.infrastructure.outbox;

import com.loopers.support.outbox.OutboxEvent;
import com.loopers.support.outbox.OutboxEventRepository;
import com.loopers.support.outbox.OutboxEventStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
        ZonedDateTime staleBefore = ZonedDateTime.now().minusSeconds(10);
        return jpaRepository.findStalePendingForUpdate(
                OutboxEventStatus.PENDING.name(), staleBefore, limit);
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
