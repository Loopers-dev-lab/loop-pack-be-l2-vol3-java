package com.loopers.batch.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "outbox.relay")
public record OutboxRelayProperties(
        boolean enabled,
        int batchSize,
        Duration sendAckTimeout
) {
    public OutboxRelayProperties {
        if (batchSize <= 0) {
            batchSize = 100;
        }
        if (sendAckTimeout == null || sendAckTimeout.isZero() || sendAckTimeout.isNegative()) {
            sendAckTimeout = Duration.ofSeconds(5);
        }
    }
}

