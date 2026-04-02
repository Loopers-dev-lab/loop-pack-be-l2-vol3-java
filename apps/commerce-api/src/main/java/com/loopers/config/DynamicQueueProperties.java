package com.loopers.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("queue.dynamic")
public record DynamicQueueProperties(
        boolean enabled,
        String metric,
        double openThreshold,
        double closeThreshold,
        int cooldownSeconds,
        int evaluationIntervalMs
) {
}
