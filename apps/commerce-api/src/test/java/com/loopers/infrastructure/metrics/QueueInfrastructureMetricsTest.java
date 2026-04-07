package com.loopers.infrastructure.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link QueueInfrastructureMetrics}가 등록하는 카운터 이름·증가만 검증한다.
 * Kafka 발행/소비 연동은 {@link com.loopers.infrastructure.queue.QueueJoinFallbackKafkaMetricsTest}를 본다.
 */
class QueueInfrastructureMetricsTest {

    private MeterRegistry meterRegistry;
    private QueueInfrastructureMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new QueueInfrastructureMetrics(meterRegistry);
    }

    @DisplayName("recordApiBackendFailure 호출 시 loopers.queue.backend.failures 카운터가 증가한다.")
    @Test
    void recordApiBackendFailure_shouldIncrementCounter() {
        metrics.recordApiBackendFailure();
        metrics.recordApiBackendFailure();

        assertThat(meterRegistry.counter("loopers.queue.backend.failures", "layer", "api").count())
                .isEqualTo(2.0);
    }

    @DisplayName("Kafka 폴백 관련 메트릭이 각각 독립적으로 증가한다.")
    @Test
    void kafkaFallbackMetrics_shouldIncrementIndependently() {
        metrics.recordKafkaJoinFallbackPublished();
        metrics.recordKafkaJoinFallbackPublishFailed();
        metrics.recordKafkaJoinFallbackRecovered();
        metrics.recordKafkaJoinFallbackDlt();

        assertThat(meterRegistry.counter("loopers.queue.join.fallback.kafka.published").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("loopers.queue.join.fallback.kafka.publish.failed").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("loopers.queue.join.fallback.recovered").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("loopers.queue.join.fallback.dlt").count()).isEqualTo(1.0);
    }

    @DisplayName("recordSchedulerLockSkipped 호출 시 loopers.queue.scheduler.lock.skipped 카운터가 증가한다.")
    @Test
    void recordSchedulerLockSkipped_shouldIncrementCounter() {
        metrics.recordSchedulerLockSkipped();

        assertThat(meterRegistry.counter("loopers.queue.scheduler.lock.skipped").count()).isEqualTo(1.0);
    }

    @DisplayName("recordSchedulerInvocation 호출 시 loopers.queue.scheduler.invocations 카운터가 증가한다.")
    @Test
    void recordSchedulerInvocation_shouldIncrementCounter() {
        metrics.recordSchedulerInvocation();
        metrics.recordSchedulerInvocation();

        assertThat(meterRegistry.counter("loopers.queue.scheduler.invocations").count()).isEqualTo(2.0);
    }

    @DisplayName("recordSchedulerTickCompleted는 released_empty 태그별로 카운터를 나눈다.")
    @Test
    void recordSchedulerTickCompleted_shouldIncrementByReleasedEmptyTag() {
        metrics.recordSchedulerTickCompleted(0);
        metrics.recordSchedulerTickCompleted(0);
        metrics.recordSchedulerTickCompleted(3);

        assertThat(meterRegistry.counter("loopers.queue.scheduler.tick.completed", "released_empty", "true").count())
                .isEqualTo(2.0);
        assertThat(meterRegistry.counter("loopers.queue.scheduler.tick.completed", "released_empty", "false").count())
                .isEqualTo(1.0);
    }

    @DisplayName("recordSseConcurrencyRejected 호출 시 loopers.queue.position.sse.concurrency.rejected 카운터가 증가한다.")
    @Test
    void recordSseConcurrencyRejected_shouldIncrementCounter() {
        metrics.recordSseConcurrencyRejected();

        assertThat(meterRegistry.counter("loopers.queue.position.sse.concurrency.rejected").count()).isEqualTo(1.0);
    }
}
