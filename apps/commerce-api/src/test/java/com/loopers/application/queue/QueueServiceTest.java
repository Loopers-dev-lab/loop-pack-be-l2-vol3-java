package com.loopers.application.queue;

import com.loopers.config.TestRedisConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestRedisConfiguration.class)
@DisplayName("QueueService 테스트")
class QueueServiceTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private QueueProperties queueProperties;

    @BeforeEach
    void setUp() {
        cleanUpRedisKeys();
    }

    @AfterEach
    void tearDown() {
        cleanUpRedisKeys();
    }

    private void cleanUpRedisKeys() {
        Set<String> queueKeys = redisTemplate.keys(queueProperties.redisKey() + "*");
        if (queueKeys != null && !queueKeys.isEmpty()) {
            redisTemplate.delete(queueKeys);
        }
        Set<String> tokenKeys = redisTemplate.keys(queueProperties.tokenPrefix() + "*");
        if (tokenKeys != null && !tokenKeys.isEmpty()) {
            redisTemplate.delete(tokenKeys);
        }
    }

    @Nested
    @DisplayName("대기열 진입")
    class Enter {

        @Test
        @DisplayName("유저가 대기열에 진입하면 Sorted Set에 추가되고 순번이 반환된다")
        void 대기열_진입_성공() {
            // Arrange
            Long userId = 1L;

            // Act
            QueueResult result = queueService.enter(userId);

            // Assert
            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.position()).isEqualTo(1);
            assertThat(result.status()).isEqualTo(QueueStatus.WAITING);
            assertThat(result.estimatedWaitSeconds()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("같은 유저가 두 번 진입해도 하나만 존재한다 (NX 옵션)")
        void 중복_진입_방지() {
            // Arrange
            Long userId = 1L;
            queueService.enter(userId);

            // Act
            QueueResult result = queueService.enter(userId);

            // Assert
            assertThat(result.status()).isEqualTo(QueueStatus.ALREADY_IN_QUEUE);

            // Sorted Set에 하나만 존재하는지 확인
            Long size = redisTemplate.opsForZSet().size("waiting-queue");
            assertThat(size).isEqualTo(1);
        }

        @Test
        @DisplayName("3명이 순서대로 진입했을 때 각각 1, 2, 3번 순번을 받는다")
        void 순번_정확성() {
            // Arrange & Act
            QueueResult result1 = queueService.enter(1L);
            QueueResult result2 = queueService.enter(2L);
            QueueResult result3 = queueService.enter(3L);

            // Assert
            assertThat(result1.position()).isEqualTo(1);
            assertThat(result2.position()).isEqualTo(2);
            assertThat(result3.position()).isEqualTo(3);
        }

        @Test
        @DisplayName("이미 토큰이 발급된 유저는 대기열 진입 불필요하며 토큰 정보를 반환한다")
        void 토큰_발급된_유저_진입() {
            // Arrange
            Long userId = 1L;
            String tokenKey = "entry-token:" + userId;
            String token = "test-token-123";
            redisTemplate.opsForValue().set(tokenKey, token);

            // Act
            QueueResult result = queueService.enter(userId);

            // Assert
            assertThat(result.status()).isEqualTo(QueueStatus.TOKEN_ISSUED);
            assertThat(result.token()).isEqualTo(token);
        }
    }

    @Nested
    @DisplayName("순번 조회")
    class GetPosition {

        @Test
        @DisplayName("대기열에 있는 유저의 순번을 조회한다")
        void 순번_조회_성공() {
            // Arrange
            queueService.enter(1L);
            queueService.enter(2L);
            queueService.enter(3L);

            // Act
            QueuePositionResult result = queueService.getPosition(2L);

            // Assert
            assertThat(result.userId()).isEqualTo(2L);
            assertThat(result.position()).isEqualTo(2);
            assertThat(result.status()).isEqualTo(QueueStatus.WAITING);
            assertThat(result.totalWaiting()).isEqualTo(3);
        }

        @Test
        @DisplayName("토큰이 발급된 유저는 position 0과 토큰 정보를 반환한다")
        void 토큰_발급된_유저_조회() {
            // Arrange
            Long userId = 1L;
            String tokenKey = "entry-token:" + userId;
            String token = "test-token-456";
            redisTemplate.opsForValue().set(tokenKey, token);

            // Act
            QueuePositionResult result = queueService.getPosition(userId);

            // Assert
            assertThat(result.position()).isEqualTo(0);
            assertThat(result.status()).isEqualTo(QueueStatus.TOKEN_ISSUED);
            assertThat(result.token()).isEqualTo(token);
        }

        @Test
        @DisplayName("대기열에 없는 유저를 조회하면 NOT_IN_QUEUE 상태를 반환한다")
        void 대기열에_없는_유저_조회() {
            // Arrange
            Long userId = 999L;

            // Act
            QueuePositionResult result = queueService.getPosition(userId);

            // Assert
            assertThat(result.status()).isEqualTo(QueueStatus.NOT_IN_QUEUE);
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class Concurrency {

        @Test
        @DisplayName("100명이 동시에 진입해도 Sorted Set에 정확히 100개가 들어간다")
        void 동시_진입_테스트() throws InterruptedException {
            // Arrange
            int threadCount = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger();

            // Act
            for (int i = 0; i < threadCount; i++) {
                long userId = i + 1;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        queueService.enter(userId);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        // ignore
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();

            // Assert
            Long size = redisTemplate.opsForZSet().size("waiting-queue");
            assertThat(size).isEqualTo(100);
            assertThat(successCount.get()).isEqualTo(100);
        }
    }
}