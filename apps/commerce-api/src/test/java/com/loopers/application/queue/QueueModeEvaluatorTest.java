package com.loopers.application.queue;

import com.loopers.config.DynamicQueueProperties;
import com.loopers.domain.queue.QueueMode;
import com.loopers.domain.queue.QueueModeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("QueueModeEvaluator 단위 테스트")
class QueueModeEvaluatorTest {

    private QueueModeRepository queueModeRepository;
    private MetricCollector hikariCollector;
    private DynamicQueueProperties dynamicProperties;
    private QueueModeEvaluator evaluator;

    @BeforeEach
    void setUp() {
        queueModeRepository = mock(QueueModeRepository.class);
        hikariCollector = mock(MetricCollector.class);
        when(hikariCollector.name()).thenReturn("hikari-pool-usage");
    }

    private QueueModeEvaluator createEvaluator(double openThreshold, double closeThreshold, int cooldownSeconds) {
        dynamicProperties = new DynamicQueueProperties(true, "hikari-pool-usage", openThreshold, closeThreshold, cooldownSeconds, 5000);
        return new QueueModeEvaluator(dynamicProperties, queueModeRepository, List.of(hikariCollector));
    }

    @Nested
    @DisplayName("BYPASS → OPEN 전환")
    class BypassToOpen {

        @Test
        @DisplayName("HikariCP 사용률이 openThreshold 이상이면 OPEN으로 전환")
        void hikari_aboveThreshold_switchesToOpen() {
            // given
            evaluator = createEvaluator(0.8, 0.5, 0);
            when(queueModeRepository.getCurrentMode()).thenReturn(QueueMode.BYPASS);
            when(hikariCollector.collect()).thenReturn(0.85);

            // when
            evaluator.evaluate();

            // then
            verify(queueModeRepository).updateMode(QueueMode.OPEN);
        }

        @Test
        @DisplayName("메트릭이 openThreshold 미만이면 BYPASS 유지")
        void belowThreshold_staysBypass() {
            // given
            evaluator = createEvaluator(0.8, 0.5, 0);
            when(queueModeRepository.getCurrentMode()).thenReturn(QueueMode.BYPASS);
            when(hikariCollector.collect()).thenReturn(0.6);

            // when
            evaluator.evaluate();

            // then
            verify(queueModeRepository, never()).updateMode(QueueMode.OPEN);
        }

        @Test
        @DisplayName("정확히 openThreshold와 같으면 OPEN으로 전환")
        void exactlyAtThreshold_switchesToOpen() {
            // given
            evaluator = createEvaluator(0.8, 0.5, 0);
            when(queueModeRepository.getCurrentMode()).thenReturn(QueueMode.BYPASS);
            when(hikariCollector.collect()).thenReturn(0.8);

            // when
            evaluator.evaluate();

            // then
            verify(queueModeRepository).updateMode(QueueMode.OPEN);
        }
    }

    @Nested
    @DisplayName("OPEN → BYPASS 전환")
    class OpenToBypass {

        @Test
        @DisplayName("메트릭이 closeThreshold 미만이고 cooldown 경과하면 BYPASS로 전환")
        void belowCloseThreshold_noCooldown_switchesToBypass() {
            // given
            evaluator = createEvaluator(0.8, 0.5, 0);
            when(queueModeRepository.getCurrentMode()).thenReturn(QueueMode.OPEN);
            when(hikariCollector.collect()).thenReturn(0.3);

            // when
            evaluator.evaluate();

            // then
            verify(queueModeRepository).updateMode(QueueMode.BYPASS);
        }

        @Test
        @DisplayName("메트릭이 closeThreshold 이상이면 OPEN 유지")
        void aboveCloseThreshold_staysOpen() {
            // given
            evaluator = createEvaluator(0.8, 0.5, 0);
            when(queueModeRepository.getCurrentMode()).thenReturn(QueueMode.OPEN);
            when(hikariCollector.collect()).thenReturn(0.6);

            // when
            evaluator.evaluate();

            // then
            verify(queueModeRepository, never()).updateMode(QueueMode.BYPASS);
        }
    }

    @Nested
    @DisplayName("히스테리시스 (flapping 방지)")
    class Hysteresis {

        @Test
        @DisplayName("openThreshold와 closeThreshold 사이의 값은 현재 모드 유지 (BYPASS)")
        void betweenThresholds_bypass_staysBypass() {
            // given
            evaluator = createEvaluator(0.8, 0.5, 0);
            when(queueModeRepository.getCurrentMode()).thenReturn(QueueMode.BYPASS);
            when(hikariCollector.collect()).thenReturn(0.65);

            // when
            evaluator.evaluate();

            // then
            verify(queueModeRepository, never()).updateMode(QueueMode.OPEN);
            verify(queueModeRepository, never()).updateMode(QueueMode.BYPASS);
        }

        @Test
        @DisplayName("openThreshold와 closeThreshold 사이의 값은 현재 모드 유지 (OPEN)")
        void betweenThresholds_open_staysOpen() {
            // given
            evaluator = createEvaluator(0.8, 0.5, 0);
            when(queueModeRepository.getCurrentMode()).thenReturn(QueueMode.OPEN);
            when(hikariCollector.collect()).thenReturn(0.65);

            // when
            evaluator.evaluate();

            // then
            verify(queueModeRepository, never()).updateMode(QueueMode.OPEN);
            verify(queueModeRepository, never()).updateMode(QueueMode.BYPASS);
        }

        @Test
        @DisplayName("cooldown 미경과 시 OPEN → BYPASS 전환 차단")
        void cooldownNotElapsed_blocksTransition() {
            // given
            evaluator = createEvaluator(0.8, 0.5, 30);
            when(queueModeRepository.getCurrentMode())
                    .thenReturn(QueueMode.BYPASS)
                    .thenReturn(QueueMode.OPEN);
            when(hikariCollector.collect())
                    .thenReturn(0.85)
                    .thenReturn(0.3);

            // when - BYPASS → OPEN (cooldown 타이머 시작)
            evaluator.evaluate();
            verify(queueModeRepository).updateMode(QueueMode.OPEN);

            // when - OPEN → BYPASS 시도 (cooldown 미경과)
            evaluator.evaluate();

            // then
            verify(queueModeRepository, never()).updateMode(QueueMode.BYPASS);
        }
    }

    @Nested
    @DisplayName("CLOSED 모드")
    class ClosedMode {

        @Test
        @DisplayName("CLOSED 상태에서는 메트릭 무관하게 CLOSED 유지")
        void closed_staysClosed() {
            // given
            evaluator = createEvaluator(0.8, 0.5, 0);
            when(queueModeRepository.getCurrentMode()).thenReturn(QueueMode.CLOSED);
            when(hikariCollector.collect()).thenReturn(0.1);

            // when
            evaluator.evaluate();

            // then
            verify(queueModeRepository, never()).updateMode(QueueMode.BYPASS);
            verify(queueModeRepository, never()).updateMode(QueueMode.OPEN);
        }
    }

    @Nested
    @DisplayName("비활성화")
    class Disabled {

        @Test
        @DisplayName("dynamic.enabled=false이면 평가하지 않음")
        void disabled_noop() {
            // given
            DynamicQueueProperties disabledProps = new DynamicQueueProperties(false, "hikari-pool-usage", 0.8, 0.5, 0, 5000);
            QueueModeEvaluator disabledEvaluator = new QueueModeEvaluator(disabledProps, queueModeRepository, List.of(hikariCollector));

            // when
            disabledEvaluator.evaluate();

            // then
            verify(queueModeRepository, never()).getCurrentMode();
            verify(hikariCollector, never()).collect();
        }
    }

    @Nested
    @DisplayName("DB 병목 시나리오 — HikariCP 메트릭 검증")
    class DbBottleneckScenario {

        @Test
        @DisplayName("점진적 부하 증가 시 80% 임계값에서 정확히 OPEN 전환")
        void gradualLoad_triggersAtThreshold() {
            // given
            double[] hikariValues = {0.50, 0.65, 0.78, 0.85, 0.92};

            int triggerIndex = -1;
            for (int i = 0; i < hikariValues.length; i++) {
                QueueModeEvaluator eval = createEvaluator(0.8, 0.5, 0);
                QueueMode mode = eval.determineNextMode(QueueMode.BYPASS, hikariValues[i]);
                if (mode == QueueMode.OPEN && triggerIndex == -1) {
                    triggerIndex = i;
                }
            }

            // then - 85% (인덱스 3)에서 처음 전환
            assertThat(triggerIndex).isEqualTo(3);
        }

        @Test
        @DisplayName("부하 해소 후 50% 이하에서 BYPASS 복귀")
        void loadRelief_returnsToBypass() {
            // given
            double[] descendingValues = {0.92, 0.75, 0.60, 0.48, 0.30};

            int bypassIndex = -1;
            for (int i = 0; i < descendingValues.length; i++) {
                QueueModeEvaluator eval = createEvaluator(0.8, 0.5, 0);
                QueueMode mode = eval.determineNextMode(QueueMode.OPEN, descendingValues[i]);
                if (mode == QueueMode.BYPASS && bypassIndex == -1) {
                    bypassIndex = i;
                }
            }

            // then - 48% (인덱스 3)에서 처음 BYPASS 복귀
            assertThat(bypassIndex).isEqualTo(3);
        }
    }
}
