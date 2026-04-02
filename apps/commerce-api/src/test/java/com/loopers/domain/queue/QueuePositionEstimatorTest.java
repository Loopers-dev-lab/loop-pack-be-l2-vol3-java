package com.loopers.domain.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueuePositionEstimatorTest {

    @Test
    @DisplayName("ceil(position / throughputTps) + 1 (Jitter 평균 1초)")
    void estimatedWaitSeconds_shouldFollowRoadmapFormula() {
        double tps = 175.0;
        assertThat(QueuePositionEstimator.estimatedWaitSeconds(0, tps)).isEqualTo(1L);
        assertThat(QueuePositionEstimator.estimatedWaitSeconds(175, tps)).isEqualTo(2L);
        assertThat(QueuePositionEstimator.estimatedWaitSeconds(176, tps)).isEqualTo(3L);
    }

    /** TC-R2-1: throughputTps=0 → denom=ε(0.001), 결정적 값으로 회귀 고정 */
    @Test
    @DisplayName("throughputTps가 0이면 ε(0.001)로 ceil(position/ε)+1")
    void estimatedWaitSeconds_withZeroTps_shouldUseEpsilonDenom() {
        assertThat(QueuePositionEstimator.estimatedWaitSeconds(10, 0.0)).isEqualTo(10_001L);
    }
}
