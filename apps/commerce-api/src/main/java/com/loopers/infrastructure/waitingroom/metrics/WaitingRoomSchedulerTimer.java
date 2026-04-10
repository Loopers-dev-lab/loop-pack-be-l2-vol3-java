package com.loopers.infrastructure.waitingroom.metrics;

import static com.loopers.infrastructure.waitingroom.metrics.WaitingRoomMetricNames.SCHEDULER_DURATION;
import static com.loopers.infrastructure.waitingroom.metrics.WaitingRoomMetricNames.SCHEDULER_RUN_TOTAL;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 스케줄러 실행 시간과 실행 횟수를 기록한다.
 * 스케줄러가 이 컴포넌트에 위임하여 Timer + Counter를 기록한다.
 */
@Component
@RequiredArgsConstructor
public class WaitingRoomSchedulerTimer {

    private final MeterRegistry registry;

    /**
     * processQueue 실행을 감싸서 실행 시간(Timer)과 실행 횟수(Counter)를 기록한다.
     */
    public void record(Runnable processQueue) {
        registry.timer(SCHEDULER_DURATION).record(processQueue);
        registry.counter(SCHEDULER_RUN_TOTAL).increment();
    }
}
