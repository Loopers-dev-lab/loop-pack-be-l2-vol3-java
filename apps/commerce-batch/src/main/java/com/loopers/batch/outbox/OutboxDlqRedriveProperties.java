package com.loopers.batch.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "outbox.dlq-redrive")
public record OutboxDlqRedriveProperties(
        boolean enabled,
        String sourceTopic,
        String dlqTopic,
        String parkingTopic,
        String groupId,
        int batchSize,
        int maxAttempts,
        Duration pollTimeout,
        Duration sendAckTimeout,
        long fixedDelayMs
) {
    public OutboxDlqRedriveProperties {
        if (sourceTopic == null || sourceTopic.isBlank()) {
            sourceTopic = "product-events";
        }
        if (dlqTopic == null || dlqTopic.isBlank()) {
            dlqTopic = sourceTopic + ".DLQ";
        }
        if (parkingTopic == null || parkingTopic.isBlank()) {
            parkingTopic = dlqTopic + ".PARK";
        }
        if (groupId == null || groupId.isBlank()) {
            groupId = "outbox-dlq-redrive";
        }
        if (batchSize <= 0) {
            batchSize = 100;
        }
        if (maxAttempts <= 0) {
            maxAttempts = 5;
        }
        if (pollTimeout == null || pollTimeout.isZero() || pollTimeout.isNegative()) {
            pollTimeout = Duration.ofSeconds(1);
        }
        if (sendAckTimeout == null || sendAckTimeout.isZero() || sendAckTimeout.isNegative()) {
            sendAckTimeout = Duration.ofSeconds(5);
        }
        if (fixedDelayMs <= 0) {
            fixedDelayMs = 5_000L;
        }
    }
}
