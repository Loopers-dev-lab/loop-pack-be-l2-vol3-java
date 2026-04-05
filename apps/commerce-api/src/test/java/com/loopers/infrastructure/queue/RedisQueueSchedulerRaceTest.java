package com.loopers.infrastructure.queue;

import com.loopers.application.queue.QueueService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "queue.scheduler.enabled=true",
        "queue.scheduler.fixed-rate=1",
        "queue.scheduler.batch-size=100"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RedisQueueSchedulerRaceTest {

    private static final String QUEUE_KEY = "order:waiting-queue";
    private static final String QUEUE_SEQUENCE_KEY = "order:waiting-queue:sequence";
    private static final String TOKEN_KEY_PREFIX = "entry-token:";

    @Autowired
    private QueueService queueService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void tearDown() {
        redisTemplate.delete(QUEUE_KEY);
        redisTemplate.delete(QUEUE_SEQUENCE_KEY);
        redisTemplate.keys(TOKEN_KEY_PREFIX + "*").forEach(redisTemplate::delete);
    }

    @DisplayName("스케줄러가 동시에 dequeue 하더라도 enter는 QUEUE_NOT_FOUND 예외를 내지 않는다.")
    @Test
    void enter_doesNotFail_whenSchedulerConsumesQueueConcurrently() throws InterruptedException {
        int threadCount = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();

        for (long userId = 1; userId <= threadCount; userId++) {
            long id = userId;
            executor.submit(() -> {
                try {
                    queueService.enter(id);
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertThat(failures).isEmpty();
    }
}
