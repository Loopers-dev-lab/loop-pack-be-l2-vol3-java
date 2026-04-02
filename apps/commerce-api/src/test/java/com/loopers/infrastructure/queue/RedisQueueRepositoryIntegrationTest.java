package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "queue.enabled=false")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RedisQueueRepositoryIntegrationTest {

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    // Nested 클래스 간 데이터 격리를 위해 각 테스트 전에도 정리
    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        redisCleanUp.truncateAll();
    }

    @Nested
    @DisplayName("addToWaitingQueue — 대기열 진입")
    class AddToWaitingQueue {

        @Test
        @DisplayName("새로운 사용자가 대기열에 진입하면 true를 반환한다")
        void addNewUser() {
            boolean added = queueRepository.addToWaitingQueue(1L, System.currentTimeMillis());

            assertThat(added).isTrue();
            assertThat(queueRepository.getTotalWaiting()).isEqualTo(1);
        }

        @Test
        @DisplayName("이미 대기 중인 사용자가 재진입하면 false를 반환하고 순번을 유지한다 (NX)")
        void reentryKeepsPosition() {
            queueRepository.addToWaitingQueue(1L, 1000.0);
            queueRepository.addToWaitingQueue(2L, 2000.0);

            boolean reAdded = queueRepository.addToWaitingQueue(1L, 9999.0);

            assertThat(reAdded).isFalse();
            Optional<Long> position = queueRepository.getPosition(1L);
            assertThat(position).isPresent();
            assertThat(position.get()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("getPosition — 순번 조회")
    class GetPosition {

        @Test
        @DisplayName("대기열에 없는 사용자의 순번은 empty이다")
        void notInQueue() {
            Optional<Long> position = queueRepository.getPosition(999L);
            assertThat(position).isEmpty();
        }

        @Test
        @DisplayName("먼저 진입한 사용자의 순번이 더 앞이다")
        void orderPreserved() {
            queueRepository.addToWaitingQueue(1L, 1000.0);
            queueRepository.addToWaitingQueue(2L, 2000.0);
            queueRepository.addToWaitingQueue(3L, 3000.0);

            assertThat(queueRepository.getPosition(1L)).contains(0L);
            assertThat(queueRepository.getPosition(2L)).contains(1L);
            assertThat(queueRepository.getPosition(3L)).contains(2L);
        }
    }

    @Nested
    @DisplayName("activateFromQueue — Lua Script 원자적 활성화")
    class ActivateFromQueue {

        @Test
        @DisplayName("앞쪽 N명을 꺼내고 토큰을 발급한다")
        void activatesTopN() {
            queueRepository.addToWaitingQueue(1L, 1000.0);
            queueRepository.addToWaitingQueue(2L, 2000.0);
            queueRepository.addToWaitingQueue(3L, 3000.0);

            List<Long> activated = queueRepository.activateFromQueue(2, 60);

            assertThat(activated).containsExactly(1L, 2L);
            assertThat(queueRepository.getTotalWaiting()).isEqualTo(1);
            assertThat(queueRepository.hasValidToken(1L)).isTrue();
            assertThat(queueRepository.hasValidToken(2L)).isTrue();
            assertThat(queueRepository.hasValidToken(3L)).isFalse();
        }

        @Test
        @DisplayName("대기열이 비어있으면 빈 리스트를 반환한다")
        void emptyQueue() {
            List<Long> activated = queueRepository.activateFromQueue(10, 60);
            assertThat(activated).isEmpty();
        }

        @Test
        @DisplayName("대기열 인원보다 많이 요청하면 있는 만큼만 활성화한다")
        void requestMoreThanAvailable() {
            queueRepository.addToWaitingQueue(1L, 1000.0);

            List<Long> activated = queueRepository.activateFromQueue(10, 60);

            assertThat(activated).hasSize(1);
            assertThat(activated).containsExactly(1L);
        }
    }

    @Nested
    @DisplayName("hasValidToken / deleteToken — 토큰 검증/삭제")
    class TokenOperations {

        @Test
        @DisplayName("활성화된 사용자의 토큰은 유효하다")
        void validToken() {
            queueRepository.addToWaitingQueue(1L, 1000.0);
            queueRepository.activateFromQueue(1, 60);

            assertThat(queueRepository.hasValidToken(1L)).isTrue();
        }

        @Test
        @DisplayName("토큰 삭제 후에는 유효하지 않다")
        void deletedToken() {
            queueRepository.addToWaitingQueue(1L, 1000.0);
            queueRepository.activateFromQueue(1, 60);

            queueRepository.deleteToken(1L);

            assertThat(queueRepository.hasValidToken(1L)).isFalse();
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTest {

        @Test
        @DisplayName("100명이 동시에 대기열에 진입해도 정확히 100명이 등록된다")
        void concurrentEntry() throws InterruptedException {
            int userCount = 100;
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch latch = new CountDownLatch(userCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 1; i <= userCount; i++) {
                long userId = i;
                executor.submit(() -> {
                    try {
                        boolean added = queueRepository.addToWaitingQueue(userId, System.currentTimeMillis());
                        if (added) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            assertThat(successCount.get()).isEqualTo(userCount);
            assertThat(queueRepository.getTotalWaiting()).isEqualTo(userCount);
        }

        @Test
        @DisplayName("토큰 TTL 1초 설정 후 만료되면 토큰이 유효하지 않다")
        void tokenExpiry() throws InterruptedException {
            queueRepository.addToWaitingQueue(1L, 1000.0);
            queueRepository.activateFromQueue(1, 1);

            assertThat(queueRepository.hasValidToken(1L)).isTrue();

            Thread.sleep(1500);

            assertThat(queueRepository.hasValidToken(1L)).isFalse();
        }

        @Test
        @DisplayName("스케줄러 배치 크기 이상의 대기자가 있어도 배치 크기만큼만 활성화된다")
        void batchSizeLimit() {
            for (int i = 1; i <= 100; i++) {
                queueRepository.addToWaitingQueue((long) i, i * 1000.0);
            }
            assertThat(queueRepository.getTotalWaiting()).isEqualTo(100);

            int batchSize = 30;
            List<Long> activated = queueRepository.activateFromQueue(batchSize, 60);

            assertThat(activated).hasSize(batchSize);
            assertThat(queueRepository.getTotalWaiting()).isEqualTo(70);

            for (Long userId : activated) {
                assertThat(queueRepository.hasValidToken(userId)).isTrue();
            }

            assertThat(queueRepository.hasValidToken(31L)).isFalse();
        }

        @Test
        @DisplayName("토큰 잔여 TTL을 조회할 수 있다")
        void tokenTtl() {
            queueRepository.addToWaitingQueue(1L, 1000.0);
            queueRepository.activateFromQueue(1, 60);

            long ttl = queueRepository.getTokenTtl(1L);

            assertThat(ttl).isGreaterThan(0);
            assertThat(ttl).isLessThanOrEqualTo(60);
        }
    }
}
