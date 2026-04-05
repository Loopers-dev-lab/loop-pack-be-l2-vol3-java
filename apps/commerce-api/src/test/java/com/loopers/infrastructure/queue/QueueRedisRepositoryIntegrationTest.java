package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueConstants;
import com.loopers.domain.queue.QueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.loopers.config.redis.RedisConfig.REDIS_TEMPLATE_MASTER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * [통합 테스트 - Redis Integration]
 *
 * 테스트 대상: QueueRedisRepository
 * 테스트 유형: 통합 테스트 (Testcontainers Redis)
 * 테스트 범위: Repository -> Redis
 */
@SpringBootTest
@DisplayName("QueueRedisRepository 통합 테스트")
class QueueRedisRepositoryIntegrationTest {

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    @Qualifier(REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate.delete(QueueConstants.QUEUE_KEY);
    }

    @Nested
    @DisplayName("대기열 진입 (enter)")
    class Enter {

        @Test
        @DisplayName("성공 - 대기열에 진입하면 true를 반환한다")
        void enter_success() {
            // given
            Long userId = 1L;
            double score = System.currentTimeMillis();

            // when
            boolean result = queueRepository.enter(userId, score);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("성공 - 동일 userId로 두 번 진입하면 두 번째는 false를 반환한다 (NX)")
        void enter_duplicate_returns_false() {
            // given
            Long userId = 1L;
            double firstScore = 1000.0;
            double secondScore = 2000.0;

            // when
            boolean first = queueRepository.enter(userId, firstScore);
            boolean second = queueRepository.enter(userId, secondScore);

            // then
            assertThat(first).isTrue();
            assertThat(second).isFalse();
        }

        @Test
        @DisplayName("성공 - 중복 진입 시 기존 score가 유지된다")
        void enter_duplicate_keeps_original_score() {
            // given
            Long userId = 1L;
            double originalScore = 1000.0;
            double laterScore = 2000.0;
            queueRepository.enter(userId, originalScore);

            // when
            queueRepository.enter(userId, laterScore);

            // then
            Optional<Long> rank = queueRepository.getRank(userId);
            assertThat(rank).isPresent();
            assertThat(rank.get()).isEqualTo(0L); // 여전히 첫 번째
        }
    }

    @Nested
    @DisplayName("순번 조회 (getRank)")
    class GetRank {

        @Test
        @DisplayName("성공 - 진입 순서대로 순번이 부여된다")
        void getRank_in_order() {
            // given
            queueRepository.enter(1L, 1000.0);
            queueRepository.enter(2L, 2000.0);
            queueRepository.enter(3L, 3000.0);

            // when & then
            assertThat(queueRepository.getRank(1L)).hasValue(0L);
            assertThat(queueRepository.getRank(2L)).hasValue(1L);
            assertThat(queueRepository.getRank(3L)).hasValue(2L);
        }

        @Test
        @DisplayName("성공 - 대기열에 없는 유저는 empty를 반환한다")
        void getRank_not_found() {
            // given & when
            Optional<Long> rank = queueRepository.getRank(999L);

            // then
            assertThat(rank).isEmpty();
        }
    }

    @Nested
    @DisplayName("전체 대기 인원 (getTotalCount)")
    class GetTotalCount {

        @Test
        @DisplayName("성공 - 진입 인원 수와 일치한다")
        void getTotalCount_matches() {
            // given
            queueRepository.enter(1L, 1000.0);
            queueRepository.enter(2L, 2000.0);
            queueRepository.enter(3L, 3000.0);

            // when
            long count = queueRepository.getTotalCount();

            // then
            assertThat(count).isEqualTo(3L);
        }

        @Test
        @DisplayName("성공 - 빈 대기열은 0을 반환한다")
        void getTotalCount_empty() {
            // given & when
            long count = queueRepository.getTotalCount();

            // then
            assertThat(count).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("원자적 꺼내기 (popFront - ZPOPMIN)")
    class PopFront {

        @Test
        @DisplayName("성공 - score 순서대로 N명을 원자적으로 꺼낸다")
        void popFront_in_order() {
            // given
            queueRepository.enter(1L, 1000.0);
            queueRepository.enter(2L, 2000.0);
            queueRepository.enter(3L, 3000.0);
            queueRepository.enter(4L, 4000.0);

            // when
            var popped = queueRepository.popFront(2);

            // then: 순서대로 2명 꺼냄
            assertThat(popped).hasSize(2);
            assertThat(popped.get(0).userId()).isEqualTo(1L);
            assertThat(popped.get(1).userId()).isEqualTo(2L);
            // 대기열에서 실제로 제거됨
            assertThat(queueRepository.getTotalCount()).isEqualTo(2L);
            assertThat(queueRepository.getRank(1L)).isEmpty();
            assertThat(queueRepository.getRank(2L)).isEmpty();
        }

        @Test
        @DisplayName("성공 - score가 정확히 보존된다 (재삽입 시 사용)")
        void popFront_preserves_score() {
            // given
            queueRepository.enter(1L, 1234.5);
            queueRepository.enter(2L, 6789.0);

            // when
            var popped = queueRepository.popFront(2);

            // then
            assertThat(popped.get(0).score()).isEqualTo(1234.5);
            assertThat(popped.get(1).score()).isEqualTo(6789.0);
        }

        @Test
        @DisplayName("성공 - 요청 수보다 대기열이 적으면 있는 만큼만 꺼낸다")
        void popFront_less_than_requested() {
            // given
            queueRepository.enter(1L, 1000.0);
            queueRepository.enter(2L, 2000.0);

            // when
            var popped = queueRepository.popFront(5);

            // then
            assertThat(popped).hasSize(2);
            assertThat(queueRepository.getTotalCount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("성공 - 빈 대기열에서 pop하면 빈 리스트를 반환한다")
        void popFront_empty_queue() {
            // given & when
            var popped = queueRepository.popFront(5);

            // then
            assertThat(popped).isEmpty();
        }

        @Test
        @DisplayName("성공 - pop 후 재삽입하면 원래 순서가 유지된다")
        void popFront_then_reinsert_preserves_order() {
            // given
            queueRepository.enter(1L, 1000.0);
            queueRepository.enter(2L, 2000.0);
            queueRepository.enter(3L, 3000.0);

            // when: 1명 꺼낸 후 재삽입
            var popped = queueRepository.popFront(1);
            queueRepository.enter(popped.get(0).userId(), popped.get(0).score());

            // then: 원래 순서 유지 (score=1000.0이므로 여전히 맨 앞)
            assertThat(queueRepository.getRank(1L)).hasValue(0L);
            assertThat(queueRepository.getRank(2L)).hasValue(1L);
            assertThat(queueRepository.getRank(3L)).hasValue(2L);
        }
    }


    @Nested
    @DisplayName("동시성 테스트")
    class Concurrency {

        @Test
        @DisplayName("성공 - 10명이 동시 진입해도 모두 고유 순번을 갖는다")
        void concurrent_enter_10_users() throws InterruptedException {
            // given
            int userCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(userCount);
            CountDownLatch latch = new CountDownLatch(userCount);
            AtomicInteger successCount = new AtomicInteger(0);

            // when
            for (int i = 1; i <= userCount; i++) {
                long userId = i;
                double score = System.currentTimeMillis() + i; // 순서 보장을 위해 offset
                executor.submit(() -> {
                    try {
                        if (queueRepository.enter(userId, score)) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            boolean completed;
            try {
                completed = latch.await(10, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }

            // then
            assertThat(completed).isTrue();
            assertThat(successCount.get()).isEqualTo(userCount);
            assertThat(queueRepository.getTotalCount()).isEqualTo(userCount);
        }

        @Test
        @DisplayName("성공 - 100명이 동시 진입해도 순서가 정확하다")
        void concurrent_enter_100_users() throws InterruptedException {
            // given
            int userCount = 100;
            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch latch = new CountDownLatch(userCount);
            AtomicInteger successCount = new AtomicInteger(0);

            // when
            for (int i = 1; i <= userCount; i++) {
                long userId = i;
                double score = i; // score를 순서대로 부여
                executor.submit(() -> {
                    try {
                        if (queueRepository.enter(userId, score)) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            boolean completed;
            try {
                completed = latch.await(10, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }

            // then
            assertThat(completed).isTrue();
            assertThat(successCount.get()).isEqualTo(userCount);
            assertThat(queueRepository.getTotalCount()).isEqualTo(userCount);

            // 순번이 score 순서대로 정확한지 확인
            for (int i = 1; i <= userCount; i++) {
                Optional<Long> rank = queueRepository.getRank((long) i);
                assertThat(rank).isPresent();
                assertThat(rank.get()).isEqualTo(i - 1L); // score=i 이므로 rank=i-1
            }
        }
    }
}
