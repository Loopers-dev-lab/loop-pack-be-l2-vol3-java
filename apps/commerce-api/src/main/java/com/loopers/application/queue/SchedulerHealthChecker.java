package com.loopers.application.queue;

import com.loopers.domain.queue.SchedulerHeartbeatRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SchedulerHealthChecker {

    static final String SCHEDULER_NAME = "token-issuer";

    private final SchedulerHeartbeatRepository heartbeatRepository;
    private final Counter tickCounter;

    public SchedulerHealthChecker(
            SchedulerHeartbeatRepository heartbeatRepository,
            MeterRegistry meterRegistry
    ) {
        this.heartbeatRepository = heartbeatRepository;
        this.tickCounter = meterRegistry.counter("queue.scheduler.ticks");
    }

    public void recordTick(int heartbeatTtlSeconds) {
        heartbeatRepository.recordHeartbeat(SCHEDULER_NAME, heartbeatTtlSeconds);
        tickCounter.increment();
    }

    public boolean isAliveByHeartbeat() {
        return heartbeatRepository.isAlive(SCHEDULER_NAME);
    }
}
