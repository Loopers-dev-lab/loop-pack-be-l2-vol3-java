package com.loopers.domain.queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.loopers.application.queue.EntryTokenScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class QueueConcurrencyTest {

    @MockitoBean
    private EntryTokenScheduler entryTokenScheduler;

    @Autowired
    private QueueService queueService;

    @Autowired
    @Qualifier("redisTemplateMaster")
    private RedisTemplate<String, String> masterRedisTemplate;

    @BeforeEach
    void setUp() {
        masterRedisTemplate.delete("queue:waiting");
    }

    @DisplayName("100명이 동시에 대기열에 진입해도 순서가 정확히 보장된다")
    @Test
    void concurrentQueueEnter() throws InterruptedException {
        // given
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1;
            executor.submit(() -> {
                try {
                    queueService.enter(userId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 중복 진입 예외는 여기선 발생하지 않아야 함
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // then
        assertThat(successCount.get()).isEqualTo(100);
        assertThat(queueService.getQueueSize()).isEqualTo(100);
    }

    @DisplayName("같은 유저가 동시에 중복 진입하면 1번만 성공한다")
    @Test
    void concurrentDuplicateEnter() throws InterruptedException {
        // given
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when — 같은 userId(1L)로 10번 동시 진입
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    queueService.enter(1L);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // then — 1번만 성공, 나머지 9번 실패
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(9);
        assertThat(queueService.getQueueSize()).isEqualTo(1);
    }
}
