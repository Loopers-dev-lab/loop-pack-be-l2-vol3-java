package com.loopers.infrastructure.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Outbox Relay 발행 메트릭.
 *
 * <p>Outbox 이벤트 발행 성공/실패 카운터와 Relay 실행 소요 시간 타이머를 등록한다.</p>
 */
@Component
@RequiredArgsConstructor
public class OutboxMetrics {

    private final MeterRegistry meterRegistry;

    private Counter publishSuccessCounter;
    private Counter publishFailCounter;
    private Timer relayTimer;

    @PostConstruct
    public void init() {
        publishSuccessCounter = Counter.builder("outbox.publish.success")
            .description("Outbox 발행 성공 건수")
            .register(meterRegistry);

        publishFailCounter = Counter.builder("outbox.publish.fail")
            .description("Outbox 발행 실패 건수")
            .register(meterRegistry);

        relayTimer = Timer.builder("outbox.relay.duration")
            .description("Outbox Relay 1회 실행 소요 시간")
            .register(meterRegistry);
    }

    public void recordPublishSuccess() {
        publishSuccessCounter.increment();
    }

    public void recordPublishFail() {
        publishFailCounter.increment();
    }

    public Timer.Sample startRelayTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopRelayTimer(Timer.Sample sample) {
        sample.stop(relayTimer);
    }
}
