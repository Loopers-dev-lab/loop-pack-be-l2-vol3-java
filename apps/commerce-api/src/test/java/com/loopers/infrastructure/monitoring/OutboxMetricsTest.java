package com.loopers.infrastructure.monitoring;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OutboxMetrics 단위 테스트")
class OutboxMetricsTest {

    private SimpleMeterRegistry registry;
    private OutboxMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new OutboxMetrics(registry);
        metrics.init();
    }

    @Test
    @DisplayName("발행 성공/실패 카운터 증가")
    void publishCounters_ShouldIncrement() {
        metrics.recordPublishSuccess();
        metrics.recordPublishSuccess();
        metrics.recordPublishFail();

        assertThat(registry.counter("outbox.publish.success").count()).isEqualTo(2.0);
        assertThat(registry.counter("outbox.publish.fail").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Relay 타이머 기록")
    void relayTimer_ShouldRecord() {
        Object timerToken = metrics.startRelayTimer();
        metrics.stopRelayTimer(timerToken);

        assertThat(registry.timer("outbox.relay.duration").count()).isEqualTo(1);
    }
}
