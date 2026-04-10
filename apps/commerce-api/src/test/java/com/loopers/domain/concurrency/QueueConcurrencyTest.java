package com.loopers.domain.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.utils.RedisCleanUp;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class QueueConcurrencyTest {

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("동시에 N명이 대기열에 진입해도, 순번이 중복 없이 고유하게 배정된다")
    void concurrency_enter_assigns_unique_positions() throws InterruptedException {
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        List<Long> positions = new CopyOnWriteArrayList<>();
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1L;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long position = waitingQueueRepository.enter(userId);
                    positions.add(position);
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        Thread.sleep(100);
        startLatch.countDown();
        finishLatch.await();
        executor.shutdown();

        assertThat(failureCount.get()).isEqualTo(0);
        assertThat(positions).hasSize(threadCount);
        assertThat(positions.stream().distinct().count()).isEqualTo(threadCount);
    }
}
