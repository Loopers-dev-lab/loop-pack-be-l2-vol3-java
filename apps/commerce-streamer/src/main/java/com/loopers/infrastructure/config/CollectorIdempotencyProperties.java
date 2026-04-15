package com.loopers.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "collector.lightweight-idempotency")
public record CollectorIdempotencyProperties(
        int redisTtlDays
) {
    public CollectorIdempotencyProperties {
        if (redisTtlDays < 1) {
            throw new IllegalArgumentException("redisTtlDays must be >= 1");
        }
    }
}
