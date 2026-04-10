package com.loopers.domain.queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QueueProperties 설정 클래스 테스트")
class QueuePropertiesTest {

    private QueueProperties properties;

    @BeforeEach
    void setUp() {
        properties = new QueueProperties();
        // 기본값 주입 (Spring 없이 단위 테스트)
        ReflectionTestUtils.setField(properties, "tokenTtlSeconds", 300);
        ReflectionTestUtils.setField(properties, "schedulerIntervalMs", 100);
        ReflectionTestUtils.setField(properties, "schedulerBatchSize", 14);
        ReflectionTestUtils.setField(properties, "maxSize", 0);
        ReflectionTestUtils.setField(properties, "enabled", true);
    }

    // --- getThroughputPerSecond ---

    @Test
    @DisplayName("batchSize=14, intervalMs=100 → 초당 처리량 140.0")
    void getThroughputPerSecond_ShouldCalculateCorrectly() {
        assertThat(properties.getThroughputPerSecond()).isEqualTo(140.0);
    }

    @Test
    @DisplayName("batchSize=10, intervalMs=200 → 초당 처리량 50.0")
    void getThroughputPerSecond_DifferentValues_ShouldCalculateCorrectly() {
        ReflectionTestUtils.setField(properties, "schedulerBatchSize", 10);
        ReflectionTestUtils.setField(properties, "schedulerIntervalMs", 200);

        assertThat(properties.getThroughputPerSecond()).isEqualTo(50.0);
    }

    // --- calculateEstimatedWaitSeconds ---

    @Test
    @DisplayName("position=700 → 700/140 = 5초 (올림)")
    void calculateEstimatedWaitSeconds_Position700_ShouldReturn5() {
        assertThat(properties.calculateEstimatedWaitSeconds(700)).isEqualTo(5);
    }

    @Test
    @DisplayName("position=0 → 0초")
    void calculateEstimatedWaitSeconds_Position0_ShouldReturn0() {
        assertThat(properties.calculateEstimatedWaitSeconds(0)).isEqualTo(0);
    }

    @Test
    @DisplayName("position=1 → 1초 (올림)")
    void calculateEstimatedWaitSeconds_Position1_ShouldReturn1() {
        assertThat(properties.calculateEstimatedWaitSeconds(1)).isEqualTo(1);
    }

    @Test
    @DisplayName("position=141 → 2초 (올림: 141/140 = 1.007)")
    void calculateEstimatedWaitSeconds_Position141_ShouldReturn2() {
        assertThat(properties.calculateEstimatedWaitSeconds(141)).isEqualTo(2);
    }

    // --- canAccept ---

    @Test
    @DisplayName("maxSize=0(무제한) → 항상 true")
    void canAccept_UnlimitedMaxSize_ShouldAlwaysReturnTrue() {
        assertThat(properties.canAccept(999999)).isTrue();
    }

    @Test
    @DisplayName("maxSize=10000, currentSize=9999 → true")
    void canAccept_UnderMaxSize_ShouldReturnTrue() {
        ReflectionTestUtils.setField(properties, "maxSize", 10000);

        assertThat(properties.canAccept(9999)).isTrue();
    }

    @Test
    @DisplayName("maxSize=10000, currentSize=10000 → false")
    void canAccept_AtMaxSize_ShouldReturnFalse() {
        ReflectionTestUtils.setField(properties, "maxSize", 10000);

        assertThat(properties.canAccept(10000)).isFalse();
    }

    @Test
    @DisplayName("maxSize=10000, currentSize=10001 → false")
    void canAccept_OverMaxSize_ShouldReturnFalse() {
        ReflectionTestUtils.setField(properties, "maxSize", 10000);

        assertThat(properties.canAccept(10001)).isFalse();
    }
}
