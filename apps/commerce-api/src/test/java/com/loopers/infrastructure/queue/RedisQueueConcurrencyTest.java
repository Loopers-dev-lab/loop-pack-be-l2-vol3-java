package com.loopers.infrastructure.queue;

import com.loopers.application.queue.QueueService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "queue.scheduler.enabled=false")
class RedisQueueConcurrencyTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String QUEUE_KEY = "order:waiting-queue";
    private static final String QUEUE_SEQUENCE_KEY = "order:waiting-queue:sequence";

    @AfterEach
    void tearDown() {
        redisTemplate.delete(QUEUE_KEY);
        redisTemplate.delete(QUEUE_SEQUENCE_KEY);
    }

    @DisplayName("동시에 여러 유저가 진입해도 모두 대기열에 정확히 등록된다.")
    @Test
    void 동시에_여러_유저가_진입해도_모두_대기열에_정확히_등록된다() throws InterruptedException {
        int threadCount = 100;
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
        assertThat(queueService.getSize()).isEqualTo(threadCount);

        List<Long> finalRanks = new ArrayList<>();
        for (long userId = 1; userId <= threadCount; userId++) {
            finalRanks.add(queueService.getRank(userId));
        }
        assertThat(finalRanks.stream().distinct().count()).isEqualTo(threadCount);
    }

    @DisplayName("배치 크기(14)를 초과하는 요청이 들어와도 초과분은 큐에 안전하게 대기한다.")
    @Test
    void 배치_크기_초과_요청이_들어와도_초과분은_큐에_남는다() throws InterruptedException {
        int batchSize = 14;
        int overflowCount = 50;
        CountDownLatch latch = new CountDownLatch(overflowCount);
        ExecutorService executor = Executors.newFixedThreadPool(overflowCount);
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();

        for (long userId = 1; userId <= overflowCount; userId++) {
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
        // 50명 모두 큐에 등록되어야 함 (스케줄러 없이)
        assertThat(queueService.getSize()).isEqualTo(overflowCount);
        // 배치 크기(14)를 초과한 인원이 큐에 남아있어야 함
        assertThat(queueService.getSize()).isGreaterThan(batchSize);
    }
}
