package com.loopers.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "queue")
public record QueueProperties(int batchSize, long schedulerIntervalMs, long tokenTtlSeconds) {
}
