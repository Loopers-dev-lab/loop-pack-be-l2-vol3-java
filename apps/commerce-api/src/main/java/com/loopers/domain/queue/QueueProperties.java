package com.loopers.domain.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "queue")
public record QueueProperties(
        int batchSize,
        int tokenTtlSeconds,
        int throughputPerSecond
) {
}
