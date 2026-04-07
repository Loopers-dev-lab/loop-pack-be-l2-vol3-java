package com.loopers.application.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code queue.scheduler.*} 바인딩. 메인 애플리케이션의 {@code @ConfigurationPropertiesScan}으로 등록한다. */
@ConfigurationProperties(prefix = "queue.scheduler")
public record EntrySchedulerProperties(
        long tickMs,
        String eventId,
        int maxBatchSize,
        long tokenTtlSeconds,
        long lockTtlSeconds,
        String lockKey,
        String heartbeatKey,
        long heartbeatTtlSeconds
) {
}
