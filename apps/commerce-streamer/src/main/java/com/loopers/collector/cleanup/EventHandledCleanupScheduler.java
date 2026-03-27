package com.loopers.collector.cleanup;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class EventHandledCleanupScheduler {

    private final EventHandledCleanupService cleanupService;
    private final EventHandledCleanupProperties properties;
    private final MeterRegistry meterRegistry;

    public EventHandledCleanupScheduler(
            EventHandledCleanupService cleanupService,
            EventHandledCleanupProperties properties,
            MeterRegistry meterRegistry) {
        this.cleanupService = cleanupService;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${collector.event-handled-cleanup.fixed-delay-ms:3600000}")
    public void cleanup() {
        if (!properties.enabled()) {
            return;
        }
        Instant cutoff = cleanupService.retentionCutoff(properties.retentionDays());
        int deleted = cleanupService.deleteOlderThanWithinSchedule(
                cutoff,
                properties.batchSize(),
                properties.maxLoopsPerRun(),
                properties.maxRowsPerRun());
        if (deleted > 0) {
            meterRegistry.counter("kafka.collector.event_handled.cleanup.deleted").increment(deleted);
        }
    }
}
