package com.loopers.batch.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "outbox.cleanup")
public record OutboxCleanupProperties(
        boolean enabled,
        Duration retention,
        int batchSize,
        int maxDeletesPerRun,
        long fixedDelayMs
) {
    public OutboxCleanupProperties {
        if (retention == null || retention.isNegative() || retention.isZero()) {
            retention = Duration.ofDays(7);
        }
        if (batchSize <= 0) {
            batchSize = 500;
        }
        if (maxDeletesPerRun <= 0) {
            maxDeletesPerRun = 10_000;
        }
        if (fixedDelayMs <= 0) {
            fixedDelayMs = 3_600_000L;
        }
    }
}
