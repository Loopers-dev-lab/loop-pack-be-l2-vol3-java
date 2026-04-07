package com.loopers.application.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueuePositionPropertiesTest {

    @Test
    @DisplayName("throughputTps가 양의 유한값이면 생성 성공")
    void constructor_withValidThroughput_shouldSucceed() {
        assertThatCode(() -> new QueuePositionProperties(175.0, null, null)).doesNotThrowAnyException();
        assertThatCode(() -> new QueuePositionProperties(QueuePositionProperties.MAX_THROUGHPUT_TPS, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("throughputTps가 0·음수·비유한·상한 초과면 IllegalArgumentException")
    void constructor_withInvalidThroughput_shouldThrow() {
        assertThatThrownBy(() -> new QueuePositionProperties(0.0, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("throughput-tps");
        assertThatThrownBy(() -> new QueuePositionProperties(-1.0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QueuePositionProperties(Double.NaN, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QueuePositionProperties(Double.POSITIVE_INFINITY, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new QueuePositionProperties(QueuePositionProperties.MAX_THROUGHPUT_TPS + 1.0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sse-stream.max-concurrent-connections가 음수면 IllegalArgumentException")
    void constructor_withNegativeSseMax_shouldThrow() {
        assertThatThrownBy(() -> new QueuePositionProperties(175.0, null, new QueuePositionProperties.SseStream(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-concurrent-connections");
    }
}
