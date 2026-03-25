package com.loopers.domain.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxEvent save(OutboxEvent event) {
        return outboxEventRepository.save(event);
    }

    public List<OutboxEvent> findUnpublished(int limit) {
        return outboxEventRepository.findUnpublished(limit);
    }
}
