package com.loopers.application.order.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("loopers.queue.order")
public record OrderQueueProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("1") long throughputPerSecond,
        @DefaultValue("1") int serverCount,
        @DefaultValue("1") long orderTpsPerServer,
        @DefaultValue("true") boolean schedulerEnabled,
        @DefaultValue("1000") long schedulerFixedDelayMs,
        @DefaultValue("10") int initialBatchSize,
        @DefaultValue("100") int maxActiveAdmissions,
        @DefaultValue("true") boolean dynamicAdjustmentEnabled,
        @DefaultValue("5000") long claimTtlMs,
        @DefaultValue("30000") long tokenTtlMs,
        @DefaultValue("key-ttl") String admissionStrategy,
        @DefaultValue("1") long minimumPollingIntervalSeconds,
        @DefaultValue("5") long maximumPollingIntervalSeconds
) {

    public long effectiveOrderThroughputPerSecond() {
        return Math.max(
                Math.max(1L, throughputPerSecond),
                Math.max(1L, serverCount) * Math.max(1L, orderTpsPerServer)
        );
    }
}
