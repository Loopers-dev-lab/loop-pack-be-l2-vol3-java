package com.loopers.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("queue")
public record QueueProperties(
        boolean enabled,
        int batchSize,
        int intervalMs,
        int tokenTtlSeconds,
        int throughputPerSecond,
        int maxSize,
        String fallbackStrategy
) {
}
