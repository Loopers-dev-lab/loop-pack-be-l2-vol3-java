package com.loopers.application.order.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("loopers.queue.order")
public record OrderQueueProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("1") long throughputPerSecond,
        @DefaultValue("true") boolean schedulerEnabled,
        @DefaultValue("1000") long schedulerFixedDelayMs,
        @DefaultValue("10") int initialBatchSize,
        @DefaultValue("100") int maxActiveAdmissions,
        @DefaultValue("true") boolean dynamicAdjustmentEnabled,
        @DefaultValue("5000") long claimTtlMs,
        @DefaultValue("30000") long tokenTtlMs,
        @DefaultValue("key-ttl") String admissionStrategy
) {
}
