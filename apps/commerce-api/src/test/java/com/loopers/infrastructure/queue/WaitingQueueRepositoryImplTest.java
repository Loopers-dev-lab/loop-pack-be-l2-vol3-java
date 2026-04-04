package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.WaitingQueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WaitingQueueRepositoryImplTest {

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate.delete("waiting-queue");
    }

    @DisplayName("원자적으로 대기열에 추가할 때, ")
    @Nested
    class AddIfNotFull {

        @DisplayName("정상적으로 추가되면, true를 반환한다.")
        @Test
        void returnsTrue_whenAdded() {
            boolean result = waitingQueueRepository.addIfNotFull(1L, 1000.0, 100);

            assertThat(result).isTrue();
            assertThat(waitingQueueRepository.rank(1L)).isZero();
        }

        @DisplayName("이미 존재하는 사용자이면, true를 반환한다.")
        @Test
        void returnsTrue_whenDuplicateUser() {
            waitingQueueRepository.addIfNotFull(1L, 1000.0, 100);

            boolean result = waitingQueueRepository.addIfNotFull(1L, 2000.0, 100);

            assertThat(result).isTrue();
        }

        @DisplayName("대기열이 가득 찼으면, false를 반환한다.")
        @Test
        void returnsFalse_whenQueueIsFull() {
            waitingQueueRepository.addIfNotFull(1L, 1000.0, 2);
            waitingQueueRepository.addIfNotFull(2L, 2000.0, 2);

            boolean result = waitingQueueRepository.addIfNotFull(3L, 3000.0, 2);

            assertThat(result).isFalse();
        }
    }

    @DisplayName("대기열에 추가할 때, ")
    @Nested
    class Add {

        @DisplayName("새로운 사용자이면, true를 반환한다.")
        @Test
        void returnsTrue_whenNewUser() {
            boolean result = waitingQueueRepository.add(1L, 1000.0);

            assertThat(result).isTrue();
        }

        @DisplayName("이미 존재하는 사용자이면, false를 반환한다.")
        @Test
        void returnsFalse_whenDuplicateUser() {
            waitingQueueRepository.add(1L, 1000.0);

            boolean result = waitingQueueRepository.add(1L, 2000.0);

            assertThat(result).isFalse();
        }
    }

    @DisplayName("순번을 조회할 때, ")
    @Nested
    class Rank {

        @DisplayName("대기열에 있으면, 0-based 순번을 반환한다.")
        @Test
        void returnsRank_whenInQueue() {
            waitingQueueRepository.add(1L, 1000.0);
            waitingQueueRepository.add(2L, 2000.0);

            assertThat(waitingQueueRepository.rank(1L)).isZero();
            assertThat(waitingQueueRepository.rank(2L)).isEqualTo(1);
        }

        @DisplayName("대기열에 없으면, null을 반환한다.")
        @Test
        void returnsNull_whenNotInQueue() {
            assertThat(waitingQueueRepository.rank(999L)).isNull();
        }
    }

    @DisplayName("대기열 크기를 조회할 때, ")
    @Nested
    class Size {

        @DisplayName("대기열 크기를 반환한다.")
        @Test
        void returnsSize() {
            waitingQueueRepository.add(1L, 1000.0);
            waitingQueueRepository.add(2L, 2000.0);

            assertThat(waitingQueueRepository.size()).isEqualTo(2);
        }

        @DisplayName("비어있으면, 0을 반환한다.")
        @Test
        void returnsZero_whenEmpty() {
            assertThat(waitingQueueRepository.size()).isZero();
        }
    }

    @DisplayName("가장 작은 score부터 꺼낼 때, ")
    @Nested
    class PopMin {

        @DisplayName("요청한 수만큼 꺼내고 대기열에서 제거한다.")
        @Test
        void popsAndRemoves_requestedCount() {
            waitingQueueRepository.add(1L, 1000.0);
            waitingQueueRepository.add(2L, 2000.0);
            waitingQueueRepository.add(3L, 3000.0);

            List<Long> result = waitingQueueRepository.popMin(2);

            assertThat(result).containsExactly(1L, 2L);
            assertThat(waitingQueueRepository.size()).isEqualTo(1);
            assertThat(waitingQueueRepository.rank(3L)).isZero();
        }

        @DisplayName("대기열이 비어있으면, 빈 리스트를 반환한다.")
        @Test
        void returnsEmptyList_whenQueueIsEmpty() {
            List<Long> result = waitingQueueRepository.popMin(5);

            assertThat(result).isEmpty();
        }
    }
}
