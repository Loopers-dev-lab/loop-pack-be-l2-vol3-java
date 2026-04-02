package com.loopers.domain.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueuePositionTest {

    private static final long SCHEDULER_INTERVAL_MS = 1000L;
    private static final int BATCH_SIZE = 1;

    @DisplayName("estimatedWaitSeconds 계산 시, ")
    @Nested
    class EstimatedWaitSeconds {

        @DisplayName("rank 가 0 이면 0 초를 반환한다.")
        @Test
        void returnsZero_whenRankIsZero() {
            // arrange
            QueuePosition position = new QueuePosition(0L);

            // act
            long result = position.estimatedWaitSeconds(SCHEDULER_INTERVAL_MS, BATCH_SIZE);

            // assert
            assertThat(result).isEqualTo(0L);
        }

        @DisplayName("rank 가 30 이면 30 초를 반환한다.")
        @Test
        void returnsThirty_whenRankIsThirty() {
            // arrange
            QueuePosition position = new QueuePosition(30L);

            // act
            long result = position.estimatedWaitSeconds(SCHEDULER_INTERVAL_MS, BATCH_SIZE);

            // assert
            assertThat(result).isEqualTo(30L);
        }
    }

    @DisplayName("nextPollAfter 계산 시, ")
    @Nested
    class NextPollAfter {

        @DisplayName("예상 대기 시간이 30초 미만이면 1 초를 반환한다.")
        @Test
        void returnsOne_whenEstimatedWaitIsLessThanThirty() {
            // arrange
            QueuePosition position = new QueuePosition(29L);

            // act
            long result = position.nextPollAfter(SCHEDULER_INTERVAL_MS, BATCH_SIZE);

            // assert
            assertThat(result).isEqualTo(1L);
        }

        @DisplayName("예상 대기 시간이 30초 이상 120초 미만이면 3 초를 반환한다.")
        @Test
        void returnsThree_whenEstimatedWaitIsBetweenThirtyAndOneTwenty() {
            // arrange
            QueuePosition position = new QueuePosition(30L);

            // act
            long result = position.nextPollAfter(SCHEDULER_INTERVAL_MS, BATCH_SIZE);

            // assert
            assertThat(result).isEqualTo(3L);
        }

        @DisplayName("예상 대기 시간이 120초 이상이면 5 초를 반환한다.")
        @Test
        void returnsFive_whenEstimatedWaitIsAtLeastOneTwenty() {
            // arrange
            QueuePosition position = new QueuePosition(120L);

            // act
            long result = position.nextPollAfter(SCHEDULER_INTERVAL_MS, BATCH_SIZE);

            // assert
            assertThat(result).isEqualTo(5L);
        }
    }
}
