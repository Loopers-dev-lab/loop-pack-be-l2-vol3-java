package com.loopers.application.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueuePositionPropertiesTest {

    @Test
    @DisplayName("throughputTps가 양의 유한값이면 생성 성공")
    void constructor_withValidThroughput_shouldSucceed() {
        assertThatCode(() -> new QueuePositionProperties(175.0, null)).doesNotThrowAnyException();
        assertThatCode(() -> new QueuePositionProperties(QueuePositionProperties.MAX_THROUGHPUT_TPS, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("throughputTps가 0·음수·비유한·상한 초과면 IllegalArgumentException")
    void constructor_withInvalidThroughput_shouldThrow() {
        assertThatThrownBy(() -> new QueuePositionProperties(0.0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("throughput-tps");
        assertThatThrownBy(() -> new QueuePositionProperties(-1.0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QueuePositionProperties(Double.NaN, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QueuePositionProperties(Double.POSITIVE_INFINITY, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new QueuePositionProperties(QueuePositionProperties.MAX_THROUGHPUT_TPS + 1.0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
