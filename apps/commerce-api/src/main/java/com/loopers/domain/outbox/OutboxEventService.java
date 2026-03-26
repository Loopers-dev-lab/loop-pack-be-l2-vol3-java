package com.loopers.domain.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxEvent save(OutboxEvent outboxEvent) {
        return outboxEventRepository.save(outboxEvent);
    }

    public List<OutboxEvent> findPendingEvents(int limit) {
        return outboxEventRepository.findAllByStatusOrderByCreatedAtAsc(OutboxStatus.INIT, limit);
    }

    public void markAsSent(OutboxEvent outboxEvent) {
        outboxEvent.markAsSent();
    }
}
