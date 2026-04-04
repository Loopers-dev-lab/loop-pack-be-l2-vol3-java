package com.loopers.application.queue;

import com.loopers.domain.queue.SchedulerHeartbeatRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SchedulerHealthChecker 단위 테스트")
class SchedulerHealthCheckerTest {

    private SchedulerHeartbeatRepository heartbeatRepository;
    private SimpleMeterRegistry meterRegistry;
    private SchedulerHealthChecker healthChecker;

    @BeforeEach
    void setUp() {
        heartbeatRepository = mock(SchedulerHeartbeatRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        healthChecker = new SchedulerHealthChecker(heartbeatRepository, meterRegistry);
    }

    @Nested
    @DisplayName("recordTick()")
    class RecordTick {

        @Test
        @DisplayName("heartbeat 기록 + Prometheus counter 증가")
        void recordsHeartbeatAndCounter() {
            // when
            healthChecker.recordTick(5);

            // then
            verify(heartbeatRepository).recordHeartbeat("token-issuer", 5);
            assertThat(meterRegistry.counter("queue.scheduler.ticks").count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("여러 번 호출 시 counter 누적")
        void multipleRecords_counterAccumulates() {
            // when
            healthChecker.recordTick(5);
            healthChecker.recordTick(5);
            healthChecker.recordTick(5);

            // then
            assertThat(meterRegistry.counter("queue.scheduler.ticks").count()).isEqualTo(3.0);
        }
    }

    @Nested
    @DisplayName("isAliveByHeartbeat()")
    class IsAlive {

        @Test
        @DisplayName("heartbeat 키 존재 → alive")
        void heartbeatExists_true() {
            when(heartbeatRepository.isAlive("token-issuer")).thenReturn(true);
            assertThat(healthChecker.isAliveByHeartbeat()).isTrue();
        }

        @Test
        @DisplayName("heartbeat 키 만료 → dead")
        void heartbeatExpired_false() {
            when(heartbeatRepository.isAlive("token-issuer")).thenReturn(false);
            assertThat(healthChecker.isAliveByHeartbeat()).isFalse();
        }
    }
}
