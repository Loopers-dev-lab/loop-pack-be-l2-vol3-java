package com.loopers.application.queue;

import com.loopers.config.QueueProperties;
import com.loopers.domain.queue.EntryTokenService;
import com.loopers.domain.queue.WaitingQueueService;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "queue.enabled=true",
        "queue.interval-ms=999999",
        "queue.dynamic.enabled=false",
        "queue.dynamic.metric=hikari-pool-usage",
        "queue.dynamic.open-threshold=0.8",
        "queue.dynamic.close-threshold=0.5",
        "queue.dynamic.cooldown-seconds=30",
        "queue.dynamic.evaluation-interval-ms=999999"
})
@DisplayName("대기열 동시성 통합 테스트")
class WaitingQueueConcurrencyTest {

    @Autowired
    private QueueApp queueApp;

    @Autowired
    private WaitingQueueService waitingQueueService;

    @Autowired
    private EntryTokenService entryTokenService;

    @Autowired
    private QueueProperties queueProperties;

    @Autowired
    private ThroughputTracker throughputTracker;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("시나리오 1: 100명 동시 진입 시 position이 1~100 범위에서 중복 없이 부여됨")
    void concurrentEnter_100users_uniquePositions() throws InterruptedException {
        // given
        int userCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch latch = new CountDownLatch(userCount);
        Set<Long> positions = ConcurrentHashMap.newKeySet();

        // when
        for (int i = 1; i <= userCount; i++) {
            long memberId = i;
            executor.submit(() -> {
                try {
                    QueueInfo info = queueApp.enterQueue(memberId);
                    positions.add(info.position());
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // then
        assertThat(positions).hasSize(userCount);
        assertThat(waitingQueueService.getTotalCount()).isEqualTo(userCount);
    }

    @Test
    @DisplayName("시나리오 3: 스케줄러 1 tick 후 batchSize만큼만 토큰 발급, 초과 발급 없음")
    void schedulerOneTick_issuesExactBatchSize() {
        // given
        int userCount = 100;
        for (int i = 1; i <= userCount; i++) {
            queueApp.enterQueue((long) i);
        }
        assertThat(waitingQueueService.getTotalCount()).isEqualTo(userCount);

        // when — 스케줄러 직접 호출 (타이밍 의존성 제거)
        QueueScheduler scheduler = new QueueScheduler(waitingQueueService, entryTokenService, queueProperties, throughputTracker);
        scheduler.issueTokens();

        // then
        int batchSize = queueProperties.batchSize();
        long remainingInQueue = waitingQueueService.getTotalCount();
        assertThat(remainingInQueue).isEqualTo(userCount - batchSize);

        int tokenCount = 0;
        for (int i = 1; i <= userCount; i++) {
            if (entryTokenService.findToken((long) i).isPresent()) {
                tokenCount++;
            }
        }
        assertThat(tokenCount).isEqualTo(batchSize);
    }
}
