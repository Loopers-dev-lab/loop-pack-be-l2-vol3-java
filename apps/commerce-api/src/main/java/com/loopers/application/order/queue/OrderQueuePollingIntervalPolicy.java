package com.loopers.application.order.queue;

import org.springframework.stereotype.Component;

@Component
public class OrderQueuePollingIntervalPolicy {

    public long resolve(long displayWaitingOrder, long estimatedWaitSeconds, OrderQueueProperties properties) {
        final long minInterval = Math.max(1L, properties.minimumPollingIntervalSeconds());
        final long maxInterval = Math.max(minInterval, properties.maximumPollingIntervalSeconds());

        if (displayWaitingOrder <= 10L || estimatedWaitSeconds <= 10L) {
            return minInterval;
        }
        if (displayWaitingOrder <= 100L || estimatedWaitSeconds <= 60L) {
            return Math.min(2L, maxInterval);
        }
        if (displayWaitingOrder <= 1_000L || estimatedWaitSeconds <= 300L) {
            return Math.min(3L, maxInterval);
        }
        return maxInterval;
    }
}
