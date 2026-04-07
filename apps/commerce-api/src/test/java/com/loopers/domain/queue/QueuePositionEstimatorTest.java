package com.loopers.domain.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueuePositionEstimatorTest {

    @Test
    @DisplayName("ceil(position / throughputTps) + 1 (Jitter 평균 1초)")
    void estimatedWaitSeconds_shouldFollowRoadmapFormula() {
        double tps = 175.0;
        assertThat(QueuePositionEstimator.estimatedWaitSeconds(0, tps)).isEqualTo(1L);
        assertThat(QueuePositionEstimator.estimatedWaitSeconds(175, tps)).isEqualTo(2L);
        assertThat(QueuePositionEstimator.estimatedWaitSeconds(176, tps)).isEqualTo(3L);
    }

    /** TC-R2-1: 비정상 TPS는 ε 대신 거부 (오설정 시 비현실적 대기 방지) */
    @Test
    @DisplayName("throughputTps가 0 이하·비유한값이면 IllegalArgumentException")
    void estimatedWaitSeconds_withNonPositiveOrNonFiniteTps_shouldThrow() {
        assertThatThrownBy(() -> QueuePositionEstimator.estimatedWaitSeconds(10, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("throughputTps");
        assertThatThrownBy(() -> QueuePositionEstimator.estimatedWaitSeconds(10, -1.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QueuePositionEstimator.estimatedWaitSeconds(10, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QueuePositionEstimator.estimatedWaitSeconds(10, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("position이 음수면 0으로 간주해 ceil(0/tps)+1")
    void estimatedWaitSeconds_withNegativePosition_shouldTreatAsZero() {
        assertThat(QueuePositionEstimator.estimatedWaitSeconds(-5, 175.0)).isEqualTo(1L);
    }
}
