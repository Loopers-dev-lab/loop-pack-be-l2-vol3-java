package com.loopers.infrastructure.cache;

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

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestRedisConfiguration.class)
@DisplayName("DistributedLockService 테스트")
class DistributedLockServiceTest {

    @Autowired
    private DistributedLockService distributedLockService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final String LOCK_KEY_PREFIX = "lock:";

    @BeforeEach
    void setUp() {
        cleanUpLockKeys();
    }

    @AfterEach
    void tearDown() {
        cleanUpLockKeys();
    }

    private void cleanUpLockKeys() {
        var keys = redisTemplate.keys(LOCK_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Nested
    @DisplayName("tryLock 테스트")
    class TryLock {

        @Test
        @DisplayName("성공 - 락 획득")
        void 락_획득_성공() {
            // Arrange
            String key = "test-resource";

            // Act
            Optional<String> lockValue = distributedLockService.tryLock(key, Duration.ofSeconds(10));

            // Assert
            assertThat(lockValue).isPresent();
        }

        @Test
        @DisplayName("실패 - 이미 락이 있을 때")
        void 이미_락_존재시_실패() {
            // Arrange
            String key = "test-resource";
            distributedLockService.tryLock(key, Duration.ofSeconds(10));

            // Act
            Optional<String> secondLock = distributedLockService.tryLock(key, Duration.ofSeconds(10));

            // Assert
            assertThat(secondLock).isEmpty();
        }

        @Test
        @DisplayName("성공 - 락 만료 후 재획득")
        void 락_만료후_재획득() throws InterruptedException {
            // Arrange
            String key = "test-resource";
            distributedLockService.tryLock(key, Duration.ofMillis(100));

            // Act - 락 만료 대기
            Thread.sleep(150);
            Optional<String> newLock = distributedLockService.tryLock(key, Duration.ofSeconds(10));

            // Assert
            assertThat(newLock).isPresent();
        }
    }

    @Nested
    @DisplayName("unlock 테스트")
    class Unlock {

        @Test
        @DisplayName("성공 - 락 해제")
        void 락_해제_성공() {
            // Arrange
            String key = "test-resource";
            Optional<String> lockValue = distributedLockService.tryLock(key, Duration.ofSeconds(10));

            // Act
            boolean released = distributedLockService.unlock(key, lockValue.orElseThrow());

            // Assert
            assertThat(released).isTrue();

            // 다시 락 획득 가능한지 확인
            Optional<String> newLock = distributedLockService.tryLock(key, Duration.ofSeconds(10));
            assertThat(newLock).isPresent();
        }

        @Test
        @DisplayName("실패 - 다른 lockValue로 해제 시도")
        void 잘못된_lockValue_해제_실패() {
            // Arrange
            String key = "test-resource";
            distributedLockService.tryLock(key, Duration.ofSeconds(10));

            // Act
            boolean released = distributedLockService.unlock(key, "wrong-value");

            // Assert
            assertThat(released).isFalse();
        }
    }

    @Nested
    @DisplayName("executeWithLock 테스트")
    class ExecuteWithLock {

        @Test
        @DisplayName("성공 - 락 획득 후 작업 실행")
        void 락_획득후_작업_실행() {
            // Arrange
            String key = "test-resource";
            AtomicInteger counter = new AtomicInteger(0);

            // Act
            Optional<Integer> result = distributedLockService.executeWithLock(
                key,
                Duration.ofSeconds(10),
                counter::incrementAndGet
            );

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(1);
            assertThat(counter.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("실패 - 락 획득 실패시 빈 값 반환")
        void 락_획득_실패시_빈값() {
            // Arrange
            String key = "test-resource";
            distributedLockService.tryLock(key, Duration.ofSeconds(10));
            AtomicInteger counter = new AtomicInteger(0);

            // Act
            Optional<Integer> result = distributedLockService.executeWithLock(
                key,
                Duration.ofSeconds(10),
                counter::incrementAndGet
            );

            // Assert
            assertThat(result).isEmpty();
            assertThat(counter.get()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class Concurrency {

        @Test
        @DisplayName("여러 스레드가 동시에 락을 요청해도 하나만 성공")
        void 동시_락_요청_단일_성공() throws InterruptedException {
            // Arrange
            String key = "test-resource";
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            // Act
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        Optional<String> lock = distributedLockService.tryLock(key, Duration.ofSeconds(10));
                        if (lock.isPresent()) {
                            successCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            endLatch.await();
            executor.shutdown();

            // Assert
            assertThat(successCount.get()).isEqualTo(1);
        }
    }
}
