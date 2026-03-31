package com.loopers.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("queue.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        double activateThreshold,
        double deactivateThreshold,
        int cooldownSeconds,
        int evaluationIntervalMs,
        int perUserMaxRequests,
        int perUserWindowSeconds
) {
}
