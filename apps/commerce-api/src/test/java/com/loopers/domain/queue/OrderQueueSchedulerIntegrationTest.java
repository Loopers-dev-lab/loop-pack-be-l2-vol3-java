package com.loopers.domain.queue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스케줄러 리더 선출 + 토큰 발급 통합 테스트.
 *
 * <p>application-test.yml에서 queue.scheduler.enabled=false로 설정하여
 * 자동 스케줄링은 비활성화하고, 수동으로 processQueue()를 호출한다.</p>
 */
@DisplayName("OrderQueueScheduler Redis 통합 테스트")
@SpringBootTest
@ActiveProfiles("test")
class OrderQueueSchedulerIntegrationTest {

    @Autowired private QueueRepository queueRepository;
    @Autowired private QueueService queueService;
    @Autowired private EntryTokenService entryTokenService;
    @Autowired private EntryTokenRepository entryTokenRepository;
    @Autowired private QueueProperties queueProperties;
    @Autowired private SchedulerLock schedulerLock;

    @Autowired
    @Qualifier("redisTemplateMaster")
    private RedisTemplate<String, String> redisTemplate;

    private static final String QUEUE_KEY = "order:waiting-queue";
    private static final String TOKEN_KEY_PREFIX = "order:entry-token:";
    private static final String LOCK_KEY = "queue:scheduler:lock";

    @BeforeEach
    void setUp() {
        redisTemplate.delete(QUEUE_KEY);
        redisTemplate.delete(LOCK_KEY);
        cleanTokenKeys();
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete(QUEUE_KEY);
        redisTemplate.delete(LOCK_KEY);
        cleanTokenKeys();
    }

    @Test
    @DisplayName("대기열에 5명 등록 후 processQueue() 호출 시 5명 모두 토큰을 받는다")
    void processQueue_ShouldIssueTokensToAllPopped() {
        // given
        for (long userId = 1; userId <= 5; userId++) {
            queueRepository.addIfAbsent(userId, (double) (1000 + userId));
        }
        assertThat(queueRepository.getSize()).isEqualTo(5);

        OrderQueueScheduler scheduler = createScheduler();

        // when
        scheduler.processQueue();

        // then
        for (long userId = 1; userId <= 5; userId++) {
            assertThat(entryTokenRepository.get(userId)).isNotNull();
        }
        assertThat(queueRepository.getSize()).isEqualTo(0);
    }

    @Test
    @DisplayName("2개 인스턴스 동시 실행 시 중복 토큰 발급이 없다 (리더 선출)")
    void processQueue_TwoInstances_ShouldNotDuplicateTokens() throws Exception {
        // given
        for (long userId = 1; userId <= 100; userId++) {
            queueRepository.addIfAbsent(userId, (double) userId);
        }

        OrderQueueScheduler scheduler1 = createScheduler();
        OrderQueueScheduler scheduler2 = createScheduler();

        int iterations = 20;
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // when
        for (int i = 0; i < iterations; i++) {
            CountDownLatch iterLatch = new CountDownLatch(1);
            executor.submit(() -> {
                try { iterLatch.await(); } catch (InterruptedException e) {}
                scheduler1.processQueue();
            });
            executor.submit(() -> {
                try { iterLatch.await(); } catch (InterruptedException e) {}
                scheduler2.processQueue();
            });
            iterLatch.countDown();
            Thread.sleep(20);
        }
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // then
        long remainingInQueue = queueRepository.getSize();
        long issuedTokens = countIssuedTokens();

        assertThat(remainingInQueue + issuedTokens).isEqualTo(100);
        assertThat(issuedTokens).isLessThanOrEqualTo(100);
    }

    private OrderQueueScheduler createScheduler() {
        return new OrderQueueScheduler(
                queueService, queueRepository, entryTokenService,
                queueProperties, schedulerLock
        );
    }

    private long countIssuedTokens() {
        Set<String> keys = redisTemplate.keys(TOKEN_KEY_PREFIX + "*");
        return keys != null ? keys.size() : 0;
    }

    private void cleanTokenKeys() {
        Set<String> keys = redisTemplate.keys(TOKEN_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
