package com.loopers.application.queue;

import com.loopers.config.QueueProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
public class ThroughputTracker {

    private final QueueProperties queueProperties;

    private final AtomicLong windowIssuedCount = new AtomicLong(0);
    private final AtomicLong windowStartMs = new AtomicLong(System.currentTimeMillis());

    private static final long WINDOW_SECONDS = 10;

    public void recordIssued(int count) {
        windowIssuedCount.addAndGet(count);
    }

    public long estimateWait(long position) {
        if (position <= 0) return 0;

        long now = System.currentTimeMillis();
        long elapsed = now - windowStartMs.get();
        long issued = windowIssuedCount.get();

        if (elapsed > WINDOW_SECONDS * 1000) {
            windowStartMs.set(now);
            windowIssuedCount.set(0);
            return fallbackEstimate(position);
        }

        if (elapsed < 1000 || issued == 0) {
            return fallbackEstimate(position);
        }

        double measuredThroughput = (double) issued / (elapsed / 1000.0);
        return (long) Math.ceil(position / measuredThroughput);
    }

    private long fallbackEstimate(long position) {
        return (long) Math.ceil((double) position / queueProperties.throughputPerSecond());
    }
}
