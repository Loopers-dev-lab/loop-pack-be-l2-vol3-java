package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@ActiveProfiles("test")
class QueueConcurrencyIntegrationTest {

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("100명이 동시에 진입해도 대기열에 정확히 100명이 등록된다")
    void 동시_진입_시_정확한_인원이_등록된다() throws InterruptedException {
        // given
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // when
        for (int i = 1; i <= threadCount; i++) {
            long userId = i;
            executor.submit(() -> {
                try {
                    boolean added = queueRepository.add("bf2024", userId);
                    if (added) successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(100);
        assertThat(queueRepository.getTotalCount("bf2024")).isEqualTo(100);
    }

    @Test
    @DisplayName("같은 유저가 50스레드에서 동시에 진입해도 1명만 등록된다")
    void 같은_유저_동시_진입_시_1명만_등록된다() throws InterruptedException {
        // given
        int threadCount = 50;
        long sameUserId = 999L;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    boolean added = queueRepository.add("bf2024", sameUserId);
                    if (added) successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(queueRepository.getTotalCount("bf2024")).isEqualTo(1);
    }
}
