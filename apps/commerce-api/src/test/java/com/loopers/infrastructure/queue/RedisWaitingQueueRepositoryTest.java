package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisWaitingQueueRepositoryTest {

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("대기열 추가(enqueue) 시, ")
    @Nested
    class Enqueue {

        @DisplayName("새로운 유저이면 true를 반환한다.")
        @Test
        void returnsTrue_whenNewUser() {
            boolean result = waitingQueueRepository.enqueue(1L, 1000.0);

            assertThat(result).isTrue();
        }

        @DisplayName("이미 존재하는 유저이면 false를 반환한다.")
        @Test
        void returnsFalse_whenDuplicateUser() {
            waitingQueueRepository.enqueue(1L, 1000.0);

            boolean result = waitingQueueRepository.enqueue(1L, 2000.0);

            assertThat(result).isFalse();
        }
    }

    @DisplayName("순번 조회(getRank) 시, ")
    @Nested
    class GetRank {

        @DisplayName("대기열에 있으면 0-based 순번을 반환한다.")
        @Test
        void returnsRank_whenInQueue() {
            waitingQueueRepository.enqueue(1L, 1000.0);
            waitingQueueRepository.enqueue(2L, 2000.0);
            waitingQueueRepository.enqueue(3L, 3000.0);

            assertThat(waitingQueueRepository.getRank(1L)).isEqualTo(0);
            assertThat(waitingQueueRepository.getRank(2L)).isEqualTo(1);
            assertThat(waitingQueueRepository.getRank(3L)).isEqualTo(2);
        }

        @DisplayName("대기열에 없으면 null을 반환한다.")
        @Test
        void returnsNull_whenNotInQueue() {
            assertThat(waitingQueueRepository.getRank(999L)).isNull();
        }
    }

    @DisplayName("전체 대기 인원(getTotalCount) 조회 시, ")
    @Nested
    class GetTotalCount {

        @DisplayName("대기열 크기를 반환한다.")
        @Test
        void returnsSize() {
            waitingQueueRepository.enqueue(1L, 1000.0);
            waitingQueueRepository.enqueue(2L, 2000.0);

            assertThat(waitingQueueRepository.getTotalCount()).isEqualTo(2);
        }

        @DisplayName("비어있으면 0을 반환한다.")
        @Test
        void returnsZero_whenEmpty() {
            assertThat(waitingQueueRepository.getTotalCount()).isZero();
        }
    }

    @DisplayName("dequeue 시, ")
    @Nested
    class Dequeue {

        @DisplayName("score가 낮은 순서대로 꺼내고 대기열에서 제거한다.")
        @Test
        void popsInScoreOrder_andRemoves() {
            waitingQueueRepository.enqueue(3L, 3000.0);
            waitingQueueRepository.enqueue(1L, 1000.0);
            waitingQueueRepository.enqueue(2L, 2000.0);

            Set<Long> result = waitingQueueRepository.dequeue(2);

            assertThat(result).containsExactly(1L, 2L);
            assertThat(waitingQueueRepository.getTotalCount()).isEqualTo(1);
            assertThat(waitingQueueRepository.getRank(3L)).isEqualTo(0);
        }

        @DisplayName("대기열이 비어있으면 빈 Set을 반환한다.")
        @Test
        void returnsEmptySet_whenQueueIsEmpty() {
            Set<Long> result = waitingQueueRepository.dequeue(5);

            assertThat(result).isEmpty();
        }

        @DisplayName("요청 수보다 대기 인원이 적으면 있는 만큼만 반환한다.")
        @Test
        void returnsAvailable_whenLessThanRequested() {
            waitingQueueRepository.enqueue(1L, 1000.0);
            waitingQueueRepository.enqueue(2L, 2000.0);

            Set<Long> result = waitingQueueRepository.dequeue(10);

            assertThat(result).containsExactly(1L, 2L);
            assertThat(waitingQueueRepository.getTotalCount()).isZero();
        }
    }
}
