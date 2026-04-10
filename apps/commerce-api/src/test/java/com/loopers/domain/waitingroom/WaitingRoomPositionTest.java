package com.loopers.domain.waitingroom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("WaitingRoomPosition 단위 테스트")
class WaitingRoomPositionTest {

    @DisplayName("대기열 순번을 생성할 때, ")
    @Nested
    class Of {

        @DisplayName("0-based rank를 1-based position으로 변환한다.")
        @Test
        void of_convertsRankToOneBasedPosition() {
            // act
            WaitingRoomPosition position = WaitingRoomPosition.of(0, 100, 175);

            // assert
            assertThat(position.position()).isEqualTo(1);
        }

        @DisplayName("예상 대기 시간을 rank / 초당 처리량으로 계산한다.")
        @Test
        void of_calculatesEstimatedWaitSeconds() {
            // act
            WaitingRoomPosition position = WaitingRoomPosition.of(300, 500, 20);

            // assert
            assertAll(
                    () -> assertThat(position.position()).isEqualTo(301),
                    () -> assertThat(position.totalWaiting()).isEqualTo(500),
                    () -> assertThat(position.estimatedWaitSeconds()).isEqualTo(15),
                    () -> assertThat(position.token()).isNull());
        }
    }
}
