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

    @Test
    @DisplayName("throughputTps가 0에 가까우면 ε으로 나눈다")
    void estimatedWaitSeconds_withNearZeroTps_shouldUseEpsilon() {
        assertThat(QueuePositionEstimator.estimatedWaitSeconds(10, 0.0)).isPositive();
    }
}
