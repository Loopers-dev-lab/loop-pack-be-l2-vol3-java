package com.loopers.application.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("QueuePositionCalculator 단위 테스트")
class QueuePositionCalculatorTest {

    @DisplayName("대기열 순번 결과를 계산할 때,")
    @Nested
    class Calculate {

        @DisplayName("rank가 0이면, 1-based position 1과 올림된 대기 시간을 반환한다.")
        @Test
        void returnsPosition1_whenRankIsZero() {
            // act
            QueuePositionResult result = QueuePositionCalculator.calculate(0, 1);

            // assert — position 1, ceil(1/5) = 1초
            assertAll(
                    () -> assertThat(result.position()).isEqualTo(1),
                    () -> assertThat(result.totalWaiting()).isEqualTo(1),
                    () -> assertThat(result.estimatedWaitSeconds()).isEqualTo(1),
                    () -> assertThat(result.pollingIntervalMs()).isEqualTo(1000),
                    () -> assertThat(result.token()).isNull()
            );
        }

        @DisplayName("처리량으로 나누어 떨어지면, 올림 없이 정확한 대기 시간을 반환한다.")
        @Test
        void returnsExactSeconds_whenDivisible() {
            // act — rank 59 → position 60, 60/5 = 12초
            QueuePositionResult result = QueuePositionCalculator.calculate(59, 100);

            // assert
            assertAll(
                    () -> assertThat(result.position()).isEqualTo(60),
                    () -> assertThat(result.totalWaiting()).isEqualTo(100),
                    () -> assertThat(result.estimatedWaitSeconds()).isEqualTo(12)
            );
        }

        @DisplayName("순번이 SOON 구간이면, 3초 폴링 주기를 반환한다.")
        @Test
        void returnsSoonPolling_whenPositionInSoonTier() {
            // act — rank 120 → position 121
            QueuePositionResult result = QueuePositionCalculator.calculate(120, 200);

            // assert — ceil(121/5) = 25초, SOON 구간
            assertAll(
                    () -> assertThat(result.position()).isEqualTo(121),
                    () -> assertThat(result.estimatedWaitSeconds()).isEqualTo(25),
                    () -> assertThat(result.pollingIntervalMs()).isEqualTo(3000)
            );
        }
    }
}
