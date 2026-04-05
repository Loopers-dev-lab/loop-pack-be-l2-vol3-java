package com.loopers.support.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "queue")
public record QueueProperties(
        boolean enabled,
        int batchSize,
        long intervalMs
) {

    public double throughputPerSecond() {
        return (double) batchSize / intervalMs * 1000;
    }
}
