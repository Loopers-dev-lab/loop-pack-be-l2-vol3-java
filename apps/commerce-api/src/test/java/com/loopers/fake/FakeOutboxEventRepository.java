package com.loopers.fake;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.outbox.OutboxStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 테스트용 Fake OutboxEventRepository.
 */
public class FakeOutboxEventRepository implements OutboxEventRepository {

    private final List<OutboxEvent> store = new ArrayList<>();
    private final AtomicLong idGenerator = new AtomicLong(0);

    @Override
    public OutboxEvent save(OutboxEvent event) {
        Long newId = idGenerator.incrementAndGet();
        OutboxEvent saved = OutboxEvent.reconstitute(
            newId,
            event.getEventId(),
            event.getEventType(),
            event.getAggregateType(),
            event.getAggregateId(),
            event.getTopic(),
            event.getPayload(),
            event.getCreatedAt(),
            event.getStatus(),
            event.getRetryCount(),
            event.getSentAt()
        );
        store.add(saved);
        return saved;
    }

    @Override
    public List<OutboxEvent> findPendingEvents(int limit) {
        return store.stream()
            .filter(e -> e.getStatus() == OutboxStatus.PENDING)
            .sorted(Comparator.comparing(OutboxEvent::getCreatedAt))
            .limit(limit)
            .toList();
    }

    @Override
    public void updateStatus(Long id, OutboxStatus status) {
        store.stream()
            .filter(e -> e.getId().equals(id))
            .findFirst()
            .ifPresent(e -> {
                if (status == OutboxStatus.SENT) {
                    e.markAsSent();
                } else if (status == OutboxStatus.FAILED) {
                    e.markAsFailed();
                }
            });
    }

    @Override
    public void markAsSent(Long id) {
        updateStatus(id, OutboxStatus.SENT);
    }

    @Override
    public void incrementRetryCount(Long id) {
        store.stream()
            .filter(e -> e.getId().equals(id))
            .findFirst()
            .ifPresent(OutboxEvent::incrementRetry);
    }

    public List<OutboxEvent> findAll() {
        return new ArrayList<>(store);
    }

    public void clear() {
        store.clear();
        idGenerator.set(0);
    }
}
