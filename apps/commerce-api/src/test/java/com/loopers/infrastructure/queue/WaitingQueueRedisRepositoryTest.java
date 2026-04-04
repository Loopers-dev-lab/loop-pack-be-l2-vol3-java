package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("WaitingQueueRedisRepository 통합 테스트")
class WaitingQueueRedisRepositoryTest {

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Nested
    @DisplayName("enter()")
    class EnterTest {

        @Test
        @DisplayName("첫 진입 시 true 반환")
        void enter_firstTime_returnsTrue() {
            // given
            Long memberId = 1L;
            double score = System.currentTimeMillis() * 1000.0;

            // when
            boolean result = waitingQueueRepository.enter(memberId, score);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("중복 진입 시 false 반환 (ZADD NX)")
        void enter_duplicate_returnsFalse() {
            // given
            Long memberId = 1L;
            double score1 = System.currentTimeMillis() * 1000.0;
            double score2 = score1 + 5000;
            waitingQueueRepository.enter(memberId, score1);

            // when
            boolean result = waitingQueueRepository.enter(memberId, score2);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("중복 진입 시 기존 순번 유지 (score 갱신 안 됨)")
        void enter_duplicate_keepsOriginalPosition() {
            // given
            Long member1 = 1L;
            Long member2 = 2L;
            waitingQueueRepository.enter(member1, 1000.0);
            waitingQueueRepository.enter(member2, 2000.0);

            // when — member1이 더 큰 score로 재진입 시도
            waitingQueueRepository.enter(member1, 9999.0);

            // then — member1의 순번은 여전히 1 (맨 앞)
            Optional<Long> position = waitingQueueRepository.getPosition(member1);
            assertThat(position).isPresent().hasValue(1L);
        }
    }

    @Nested
    @DisplayName("getPosition()")
    class GetPositionTest {

        @Test
        @DisplayName("미진입 유저는 empty 반환")
        void getPosition_notEntered_returnsEmpty() {
            // when
            Optional<Long> position = waitingQueueRepository.getPosition(999L);

            // then
            assertThat(position).isEmpty();
        }

        @Test
        @DisplayName("순번은 1-based로 반환")
        void getPosition_returnsOneBased() {
            // given
            waitingQueueRepository.enter(1L, 1000.0);
            waitingQueueRepository.enter(2L, 2000.0);
            waitingQueueRepository.enter(3L, 3000.0);

            // when & then
            assertThat(waitingQueueRepository.getPosition(1L)).hasValue(1L);
            assertThat(waitingQueueRepository.getPosition(2L)).hasValue(2L);
            assertThat(waitingQueueRepository.getPosition(3L)).hasValue(3L);
        }
    }

    @Nested
    @DisplayName("getTotalCount()")
    class GetTotalCountTest {

        @Test
        @DisplayName("빈 대기열은 0 반환")
        void getTotalCount_empty_returnsZero() {
            // when & then
            assertThat(waitingQueueRepository.getTotalCount()).isZero();
        }

        @Test
        @DisplayName("진입 수만큼 정확히 카운트")
        void getTotalCount_returnsExactCount() {
            // given
            waitingQueueRepository.enter(1L, 1000.0);
            waitingQueueRepository.enter(2L, 2000.0);
            waitingQueueRepository.enter(3L, 3000.0);

            // when & then
            assertThat(waitingQueueRepository.getTotalCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("popN()")
    class PopNTest {

        @Test
        @DisplayName("빈 대기열에서 popN 시 빈 리스트 반환")
        void popN_empty_returnsEmptyList() {
            // when
            List<Long> result = waitingQueueRepository.popN(5);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("정확히 n개만 꺼냄")
        void popN_returnsExactCount() {
            // given
            for (long i = 1; i <= 10; i++) {
                waitingQueueRepository.enter(i, i * 1000.0);
            }

            // when
            List<Long> result = waitingQueueRepository.popN(3);

            // then
            assertThat(result).hasSize(3);
            assertThat(waitingQueueRepository.getTotalCount()).isEqualTo(7);
        }

        @Test
        @DisplayName("score 순서대로 꺼냄 (FIFO)")
        void popN_returnsInScoreOrder() {
            // given
            waitingQueueRepository.enter(3L, 3000.0);
            waitingQueueRepository.enter(1L, 1000.0);
            waitingQueueRepository.enter(2L, 2000.0);

            // when
            List<Long> result = waitingQueueRepository.popN(3);

            // then — score 오름차순: 1L(1000) → 2L(2000) → 3L(3000)
            assertThat(result).containsExactly(1L, 2L, 3L);
        }

        @Test
        @DisplayName("대기열 인원보다 많이 요청 시 있는 만큼만 반환")
        void popN_requestMoreThanAvailable_returnsAll() {
            // given
            waitingQueueRepository.enter(1L, 1000.0);
            waitingQueueRepository.enter(2L, 2000.0);

            // when
            List<Long> result = waitingQueueRepository.popN(10);

            // then
            assertThat(result).hasSize(2);
            assertThat(waitingQueueRepository.getTotalCount()).isZero();
        }

        @Test
        @DisplayName("popN 후 꺼낸 멤버는 대기열에서 제거됨")
        void popN_removesFromQueue() {
            // given
            waitingQueueRepository.enter(1L, 1000.0);
            waitingQueueRepository.enter(2L, 2000.0);
            waitingQueueRepository.enter(3L, 3000.0);

            // when
            waitingQueueRepository.popN(2);

            // then
            assertThat(waitingQueueRepository.getPosition(1L)).isEmpty();
            assertThat(waitingQueueRepository.getPosition(2L)).isEmpty();
            assertThat(waitingQueueRepository.getPosition(3L)).hasValue(1L);
        }
    }
}
