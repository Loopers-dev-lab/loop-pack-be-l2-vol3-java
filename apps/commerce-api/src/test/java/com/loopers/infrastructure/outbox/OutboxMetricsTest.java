package com.loopers.infrastructure.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("OutboxMetrics — 메트릭 수집 테스트")
class OutboxMetricsTest {

    private MeterRegistry registry;

    @Mock
    private OutboxEventJpaRepository repository;

    private OutboxMetrics metrics;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        registry = new SimpleMeterRegistry();
        metrics = new OutboxMetrics(registry, repository);
    }

    @Test
    @DisplayName("발행 성공 카운터가 증가한다")
    void recordPublishSuccess_increments_counter() {
        // when
        metrics.recordPublishSuccess();
        metrics.recordPublishSuccess();
        metrics.recordPublishSuccess();

        // then
        double count = registry.counter("outbox.publish.success").count();
        assertThat(count).isEqualTo(3.0);
    }

    @Test
    @DisplayName("발행 실패 카운터가 증가한다")
    void recordPublishFailure_increments_counter() {
        // when
        metrics.recordPublishFailure();
        metrics.recordPublishFailure();

        // then
        double count = registry.counter("outbox.publish.failed").count();
        assertThat(count).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Phase 1 처리 시간을 기록한다")
    void recordPhase1Duration() {
        // when
        metrics.recordPhase1Duration(150);
        metrics.recordPhase1Duration(200);

        // then
        double totalTime = registry.timer("outbox.phase1.duration").totalTime(java.util.concurrent.TimeUnit.MILLISECONDS);
        assertThat(totalTime).isEqualTo(350.0);
    }

    @Test
    @DisplayName("Phase 2 처리 시간을 기록한다")
    void recordPhase2Duration() {
        // when
        metrics.recordPhase2Duration(500);
        metrics.recordPhase2Duration(300);

        // then
        double totalTime = registry.timer("outbox.phase2.duration").totalTime(java.util.concurrent.TimeUnit.MILLISECONDS);
        assertThat(totalTime).isEqualTo(800.0);
    }

    @Test
    @DisplayName("PENDING 개수 Gauge가 등록되어 있다")
    void pendingCountGaugeIsRegistered() {
        // when
        when(repository.countByStatus(OutboxStatus.PENDING)).thenReturn(5L);

        // then
        assertThat(registry.find("outbox.pending.count").gauge()).isNotNull();
    }

    @Test
    @DisplayName("PROCESSING 개수 Gauge가 등록되어 있다")
    void processingCountGaugeIsRegistered() {
        // when
        when(repository.countByStatus(OutboxStatus.PROCESSING)).thenReturn(3L);

        // then
        assertThat(registry.find("outbox.processing.count").gauge()).isNotNull();
    }

    @Test
    @DisplayName("모든 타이머가 등록되어 있다")
    void timersAreRegistered() {
        assertThat(registry.find("outbox.phase1.duration").timer()).isNotNull();
        assertThat(registry.find("outbox.phase2.duration").timer()).isNotNull();
    }
}
