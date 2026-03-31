package com.loopers.application.queue;

import com.loopers.config.QueueProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ThroughputTracker 단위 테스트")
class ThroughputTrackerTest {

    private ThroughputTracker tracker;

    @BeforeEach
    void setUp() {
        QueueProperties properties = new QueueProperties(true, 14, 100, 300, 140, 100000);
        tracker = new ThroughputTracker(properties);
    }

    @Test
    @DisplayName("측정 데이터 없으면 fallback(position / throughputPerSecond)으로 계산")
    void estimateWait_noData_fallsBackToFixed() {
        long result = tracker.estimateWait(280);
        assertThat(result).isEqualTo(2);
    }

    @Test
    @DisplayName("position이 0이면 0 반환")
    void estimateWait_zeroPosition_returnsZero() {
        assertThat(tracker.estimateWait(0)).isZero();
    }

    @Test
    @DisplayName("recordIssued 후 측정 기반 추정 — fallback과 다른 결과")
    void estimateWait_afterRecord_usesMeasured() throws InterruptedException {
        tracker.recordIssued(100);
        Thread.sleep(1100);

        long result = tracker.estimateWait(100);

        assertThat(result).isGreaterThan(0);
    }
}
