package com.loopers.application.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "queue")
public record QueueProperties(
    int throughputPerSecond,
    String redisKey,
    String tokenPrefix,
    int tokenTtlSeconds,
    boolean schedulerEnabled
) {
    public QueueProperties {
        if (throughputPerSecond <= 0) {
            throughputPerSecond = 175;
        }
        if (redisKey == null || redisKey.isBlank()) {
            redisKey = "waiting-queue";
        }
        if (tokenPrefix == null || tokenPrefix.isBlank()) {
            tokenPrefix = "entry-token";
        }
        if (tokenTtlSeconds <= 0) {
            tokenTtlSeconds = 300;
        }
    }
}
