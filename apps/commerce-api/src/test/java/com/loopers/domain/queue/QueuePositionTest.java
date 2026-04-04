package com.loopers.domain.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueuePositionTest {

    @DisplayName("QueuePosition을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("0-based rank를 1-based position으로 변환하고 예상 대기 시간을 계산한다.")
        @Test
        void calculatesEstimatedWait_whenCreatedWithRankAndTps() {
            QueuePosition position = QueuePosition.of(349, 175);

            assertThat(position.position()).isEqualTo(350);
            assertThat(position.estimatedWaitSeconds()).isEqualTo(2);
        }

        @DisplayName("tps가 0이면, 예상 대기 시간은 0이다.")
        @Test
        void returnsZeroWait_whenTpsIsZero() {
            QueuePosition position = QueuePosition.of(99, 0);

            assertThat(position.estimatedWaitSeconds()).isZero();
        }

        @DisplayName("0-based rank 0은 position 1이 된다.")
        @Test
        void returnsPositionOne_whenRankIsZero() {
            QueuePosition position = QueuePosition.of(0, 175);

            assertThat(position.position()).isEqualTo(1);
        }
    }

    @DisplayName("동적 retryAfter를 계산할 때, ")
    @Nested
    class RetryAfter {

        @DisplayName("순번이 100 이하이면, retryAfter는 1초이다.")
        @Test
        void returnsOneSecond_whenPositionIsUnder100() {
            QueuePosition position = QueuePosition.of(49, 175);

            assertThat(position.retryAfter()).isEqualTo(1);
        }

        @DisplayName("순번이 100 초과 1000 이하이면, retryAfter는 2초이다.")
        @Test
        void returnsTwoSeconds_whenPositionIsBetween100And1000() {
            QueuePosition position = QueuePosition.of(499, 175);

            assertThat(position.retryAfter()).isEqualTo(2);
        }

        @DisplayName("순번이 1000 초과 10000 이하이면, retryAfter는 3초이다.")
        @Test
        void returnsThreeSeconds_whenPositionIsBetween1000And10000() {
            QueuePosition position = QueuePosition.of(4999, 175);

            assertThat(position.retryAfter()).isEqualTo(3);
        }

        @DisplayName("순번이 10000 초과이면, retryAfter는 5초이다.")
        @Test
        void returnsFiveSeconds_whenPositionIsOver10000() {
            QueuePosition position = QueuePosition.of(19999, 175);

            assertThat(position.retryAfter()).isEqualTo(5);
        }
    }
}
