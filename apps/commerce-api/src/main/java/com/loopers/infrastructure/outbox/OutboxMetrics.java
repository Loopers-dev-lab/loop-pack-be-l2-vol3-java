package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics {

    public OutboxMetrics(MeterRegistry registry, OutboxRepository outboxRepository) {
        Gauge.builder("outbox.pending.count", outboxRepository, OutboxRepository::countPending)
                .description("Number of PENDING outbox entries awaiting relay")
                .register(registry);
    }
}
