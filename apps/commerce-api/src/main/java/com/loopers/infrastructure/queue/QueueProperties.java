package com.loopers.infrastructure.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "queue")
public record QueueProperties(int batchSize, long schedulerIntervalMs, long tokenTtlSeconds) {
}
