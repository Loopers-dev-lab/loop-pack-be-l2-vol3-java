package com.loopers.application.queue;

import com.loopers.config.QueueProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
public class ThroughputTracker {

    private final QueueProperties queueProperties;

    private final AtomicLong windowIssuedCount = new AtomicLong(0);
    private final AtomicLong windowStartMs = new AtomicLong(System.currentTimeMillis());

    private final AtomicReference<Double> emaWaitSeconds = new AtomicReference<>(0.0);
    private final AtomicLong lastEmaPosition = new AtomicLong(0);

    private static final double EMA_ALPHA = 0.3;
    private static final long WINDOW_SECONDS = 10;

    public void recordIssued(int count) {
        windowIssuedCount.addAndGet(count);
    }

    public void recordActualWait(long position, double actualWaitSeconds) {
        if (position <= 0) return;
        double perPositionWait = actualWaitSeconds / position;
        emaWaitSeconds.updateAndGet(prev -> {
            if (prev <= 0) return perPositionWait;
            return EMA_ALPHA * perPositionWait + (1 - EMA_ALPHA) * prev;
        });
        lastEmaPosition.set(position);
    }

    public long estimateWaitA(long position) {
        if (position <= 0) return 0;
        return (long) Math.ceil((double) position / queueProperties.throughputPerSecond());
    }

    public long estimateWaitB(long position) {
        if (position <= 0) return 0;

        long now = System.currentTimeMillis();
        long elapsed = now - windowStartMs.get();
        long issued = windowIssuedCount.get();

        if (elapsed > WINDOW_SECONDS * 1000) {
            windowStartMs.set(now);
            windowIssuedCount.set(0);
            return estimateWaitA(position);
        }

        if (elapsed < 1000 || issued == 0) {
            return estimateWaitA(position);
        }

        double measuredThroughput = (double) issued / (elapsed / 1000.0);
        return (long) Math.ceil(position / measuredThroughput);
    }

    public long estimateWaitC(long position) {
        if (position <= 0) return 0;

        double perPositionWait = emaWaitSeconds.get();
        if (perPositionWait <= 0) {
            return estimateWaitA(position);
        }

        return (long) Math.ceil(position * perPositionWait);
    }
}
