package com.loopers.application.queue;

import static com.loopers.application.queue.QueuePollingPolicy.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("QueuePollingPolicy 단위 테스트")
class QueuePollingPolicyTest {

    @DisplayName("폴링 주기를 산정할 때,")
    @Nested
    class CalculateIntervalMs {

        @DisplayName("순번 1이면, IMMINENT 구간 폴링 주기(1초)를 반환한다.")
        @Test
        void returnsImminentPolling_whenPositionIsOne() {
            assertThat(QueuePollingPolicy.calculateIntervalMs(1)).isEqualTo(POLLING_IMMINENT_MS);
        }

        @DisplayName("순번 60이면, IMMINENT 구간 상한으로 1초를 반환한다.")
        @Test
        void returnsImminentPolling_whenPositionIsImminentMax() {
            assertThat(QueuePollingPolicy.calculateIntervalMs(60)).isEqualTo(POLLING_IMMINENT_MS);
        }

        @DisplayName("순번 61이면, SOON 구간 하한으로 3초를 반환한다.")
        @Test
        void returnsSoonPolling_whenPositionIsSoonMin() {
            assertThat(QueuePollingPolicy.calculateIntervalMs(61)).isEqualTo(POLLING_SOON_MS);
        }

        @DisplayName("순번 300이면, SOON 구간 상한으로 3초를 반환한다.")
        @Test
        void returnsSoonPolling_whenPositionIsSoonMax() {
            assertThat(QueuePollingPolicy.calculateIntervalMs(300)).isEqualTo(POLLING_SOON_MS);
        }

        @DisplayName("순번 301이면, MODERATE 구간 하한으로 10초를 반환한다.")
        @Test
        void returnsModeratePolling_whenPositionIsModerateMin() {
            assertThat(QueuePollingPolicy.calculateIntervalMs(301)).isEqualTo(POLLING_MODERATE_MS);
        }

        @DisplayName("순번 3000이면, MODERATE 구간 상한으로 10초를 반환한다.")
        @Test
        void returnsModeratePolling_whenPositionIsModerateMax() {
            assertThat(QueuePollingPolicy.calculateIntervalMs(3000)).isEqualTo(POLLING_MODERATE_MS);
        }

        @DisplayName("순번 3001이면, FAR 구간 하한으로 20초를 반환한다.")
        @Test
        void returnsFarPolling_whenPositionIsFarMin() {
            assertThat(QueuePollingPolicy.calculateIntervalMs(3001)).isEqualTo(POLLING_FAR_MS);
        }
    }
}
