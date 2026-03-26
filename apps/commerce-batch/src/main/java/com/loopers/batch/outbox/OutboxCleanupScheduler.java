package com.loopers.batch.outbox;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class OutboxCleanupScheduler {

    private final OutboxCleanupService outboxCleanupService;
    private final OutboxCleanupProperties properties;

    public OutboxCleanupScheduler(OutboxCleanupService outboxCleanupService, OutboxCleanupProperties properties) {
        this.outboxCleanupService = outboxCleanupService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${outbox.cleanup.fixed-delay-ms}")
    public void cleanupPublishedHistory() {
        if (!properties.enabled()) {
            return;
        }
        Instant cutoff = Instant.now().minus(properties.retention());
        int totalDeleted = 0;
        while (totalDeleted < properties.maxDeletesPerRun()) {
            int batch = Math.min(properties.batchSize(), properties.maxDeletesPerRun() - totalDeleted);
            int deleted = outboxCleanupService.deletePublishedOlderThan(cutoff, batch);
            totalDeleted += deleted;
            if (deleted == 0) {
                break;
            }
        }
    }
}
