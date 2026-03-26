package com.loopers.infrastructure.outbox.relay;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class OutboxEventPoller {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxEventPoller(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public List<OutboxEvent> poll(int limit) {
        List<OutboxEvent> candidates = outboxEventRepository.findPublishCandidates(ZonedDateTime.now(), limit);
        List<OutboxEvent> processing = new ArrayList<>();
        ZonedDateTime processingDeadline = ZonedDateTime.now().plusSeconds(30);
        for (OutboxEvent candidate : candidates) {
            boolean marked = outboxEventRepository.markProcessing(candidate.id(), processingDeadline);
            if (marked) {
                processing.add(candidate);
            }
        }
        return processing;
    }
}
