package com.loopers.domain.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [단위 테스트 - VO]
//
// 테스트 대상: QueuePositionInfo
// 테스트 유형: 단위 테스트 (Mock/Stub 없음, 순수 VO 검증)
// 테스트 범위: 대기 순번 정보 생성 및 계산 로직
//
// waiting() 팩토리 메서드:
//   - rank(0-based) → position(1-based) 변환
//   - 예상 대기 시간 계산 (position / batchSize * intervalMs)
//   - polling 주기 티어 계산 (1~100: 1초, 101~1000: 3초, 1001+: 5초)
//
// ready() 팩토리 메서드:
//   - 입장 가능 상태 생성 (position=0, token 포함)
@DisplayName("QueuePositionInfo 단위 테스트")
class QueuePositionInfoTest {

    @Nested
    @DisplayName("waiting - 대기 중 상태 생성")
    class Waiting {

        @Test
        @DisplayName("rank=0이면 position=1이고 예상 대기 시간은 0이다")
        void rank_0_position_1_wait_0() {
            // given
            long rank = 0;

            // when
            QueuePositionInfo info = QueuePositionInfo.waiting(rank, 18, 100);

            // then
            assertThat(info.position()).isEqualTo(1L);
            assertThat(info.estimatedWaitSeconds()).isEqualTo(0L);
            assertThat(info.token()).isNull();
            assertThat(info.pollIntervalSeconds()).isEqualTo(1);
        }

        @Test
        @DisplayName("rank=17이면 position=18이고 예상 대기 시간은 0초(반올림)이다")
        void rank_17_position_18() {
            // given
            long rank = 17;

            // when
            QueuePositionInfo info = QueuePositionInfo.waiting(rank, 18, 100);

            // then
            assertThat(info.position()).isEqualTo(18L);
            // 18 / 18 * 0.1 = 0.1 → Math.round → 0
            assertThat(info.estimatedWaitSeconds()).isEqualTo(0L);
        }

        @Test
        @DisplayName("rank=175이면 position=176이고 예상 대기 시간은 1초이다")
        void rank_175_position_176_wait_1() {
            // given
            long rank = 175;

            // when
            QueuePositionInfo info = QueuePositionInfo.waiting(rank, 18, 100);

            // then
            assertThat(info.position()).isEqualTo(176L);
            // 176 / 18 * 0.1 ≈ 0.978 → Math.round → 1
            assertThat(info.estimatedWaitSeconds()).isEqualTo(1L);
        }

        @Test
        @DisplayName("rank=350이면 position=351이고 예상 대기 시간은 2초이다")
        void rank_350_position_351_wait_2() {
            // given
            long rank = 350;

            // when
            QueuePositionInfo info = QueuePositionInfo.waiting(rank, 18, 100);

            // then
            assertThat(info.position()).isEqualTo(351L);
            // 351 / 18 * 0.1 ≈ 1.95 → Math.round → 2
            assertThat(info.estimatedWaitSeconds()).isEqualTo(2L);
        }

        @Test
        @DisplayName("대규모 대기(rank=9999) 시 예상 대기 시간이 정확하다")
        void large_rank_estimated_wait() {
            // given
            long rank = 9999;

            // when
            QueuePositionInfo info = QueuePositionInfo.waiting(rank, 18, 100);

            // then
            assertThat(info.position()).isEqualTo(10000L);
            // 10000 / 18 * 0.1 ≈ 55.56 → Math.round → 56
            assertThat(info.estimatedWaitSeconds()).isEqualTo(56L);
        }

        @Test
        @DisplayName("batchSize 배수(rank=35, position=36)에서 나머지 없이 정확하다")
        void exact_batch_multiple() {
            // given
            long rank = 35;

            // when
            QueuePositionInfo info = QueuePositionInfo.waiting(rank, 18, 100);

            // then
            assertThat(info.position()).isEqualTo(36L);
            // 36 / 18 * 0.1 = 0.2 → Math.round → 0
            assertThat(info.estimatedWaitSeconds()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("waiting - polling 주기 계산")
    class PollInterval {

        @Test
        @DisplayName("position=1 → polling 주기 1초")
        void position_1_poll_1() {
            // given & when
            QueuePositionInfo info = QueuePositionInfo.waiting(0, 18, 100);

            // then
            assertThat(info.pollIntervalSeconds()).isEqualTo(1);
        }

        @Test
        @DisplayName("position=100 → polling 주기 1초 (경계값)")
        void position_100_poll_1() {
            // given & when
            QueuePositionInfo info = QueuePositionInfo.waiting(99, 18, 100);

            // then
            assertThat(info.position()).isEqualTo(100L);
            assertThat(info.pollIntervalSeconds()).isEqualTo(1);
        }

        @Test
        @DisplayName("position=101 → polling 주기 3초 (경계값)")
        void position_101_poll_3() {
            // given & when
            QueuePositionInfo info = QueuePositionInfo.waiting(100, 18, 100);

            // then
            assertThat(info.position()).isEqualTo(101L);
            assertThat(info.pollIntervalSeconds()).isEqualTo(3);
        }

        @Test
        @DisplayName("position=1000 → polling 주기 3초 (경계값)")
        void position_1000_poll_3() {
            // given & when
            QueuePositionInfo info = QueuePositionInfo.waiting(999, 18, 100);

            // then
            assertThat(info.position()).isEqualTo(1000L);
            assertThat(info.pollIntervalSeconds()).isEqualTo(3);
        }

        @Test
        @DisplayName("position=1001 → polling 주기 5초 (경계값)")
        void position_1001_poll_5() {
            // given & when
            QueuePositionInfo info = QueuePositionInfo.waiting(1000, 18, 100);

            // then
            assertThat(info.position()).isEqualTo(1001L);
            assertThat(info.pollIntervalSeconds()).isEqualTo(5);
        }

        @Test
        @DisplayName("position=5000 → polling 주기 5초")
        void position_5000_poll_5() {
            // given & when
            QueuePositionInfo info = QueuePositionInfo.waiting(4999, 18, 100);

            // then
            assertThat(info.position()).isEqualTo(5000L);
            assertThat(info.pollIntervalSeconds()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("ready - 입장 가능 상태 생성")
    class Ready {

        @Test
        @DisplayName("position=0, estimatedWaitSeconds=0, token이 포함된다")
        void ready_has_token() {
            // given
            String token = "test-token-uuid";

            // when
            QueuePositionInfo info = QueuePositionInfo.ready(token);

            // then
            assertThat(info.position()).isEqualTo(0L);
            assertThat(info.estimatedWaitSeconds()).isEqualTo(0L);
            assertThat(info.token()).isEqualTo(token);
            assertThat(info.pollIntervalSeconds()).isEqualTo(0);
        }
    }
}
