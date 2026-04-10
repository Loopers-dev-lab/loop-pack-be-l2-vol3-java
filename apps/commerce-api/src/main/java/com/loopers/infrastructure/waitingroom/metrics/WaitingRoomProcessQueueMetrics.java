package com.loopers.infrastructure.waitingroom.metrics;

import static com.loopers.infrastructure.waitingroom.metrics.WaitingRoomMetricNames.ADMIT_BATCH_SIZE;
import static com.loopers.infrastructure.waitingroom.metrics.WaitingRoomMetricNames.WAIT_DURATION;

import com.loopers.domain.waitingroom.WaitingEntry;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WaitingRoomProcessQueueMetrics {

    private final MeterRegistry registry;

    public void recordBatch(List<WaitingEntry> admitted) {
        registry.summary(ADMIT_BATCH_SIZE).record(admitted.size());
        long now = System.currentTimeMillis();
        for (WaitingEntry entry : admitted) {
            registry.timer(WAIT_DURATION)
                    .record(Duration.ofMillis(now - entry.enterTimeMillis()));
        }
    }
}
