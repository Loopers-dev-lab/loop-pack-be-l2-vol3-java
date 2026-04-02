package com.loopers.application.order.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("loopers.queue.order")
public record OrderQueueProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("1") long throughputPerSecond
) {
}
