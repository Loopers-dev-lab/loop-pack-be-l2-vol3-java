package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.EntrySchedulerService;
import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.JitterDelay;
import com.loopers.domain.queue.OrderEntryTokenService;
import com.loopers.domain.queue.SchedulerLockRepository;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.support.error.CoreException;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * Round 8 Quest 검증: 동시 진입 순서 보장, 입장 토큰 TTL 만료, 배치보다 큰 대기열 다중 틱 방출.
 */
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
class QueueQuestVerificationIntegrationTest {

    private static final String EVENT_ID = "quest-verification-queue";
    private static final int CONCURRENT_USER_COUNT = 50;
    private static final int BATCH_SIZE = 18;
    private static final String LOCK_KEY = "queue:scheduler:lock:quest-test";
    private static final String HEARTBEAT_KEY = "queue:scheduler:heartbeat:quest-test";

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

    @Autowired
    private EntryTokenRepository entryTokenRepository;

    @Autowired
    private EntrySchedulerService entrySchedulerService;

    @Autowired
    private OrderEntryTokenService orderEntryTokenService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @MockBean
    private JitterDelay jitterDelay;

    @MockBean
    private SchedulerLockRepository schedulerLockRepository;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
        doNothing().when(jitterDelay).delay(anyLong());
        when(schedulerLockRepository.tryAcquireLock(anyString(), anyString(), anyLong())).thenReturn(true);
        doNothing().when(schedulerLockRepository).updateHeartbeat(anyString(), anyString(), anyLong());
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("동시 진입 후 pop 시 score(FIFO) 순서가 유지된다.")
    @Test
    void concurrentJoin_shouldPreservePopOrderByScore() throws Exception {
        List<Long> launchOrder = LongStream.rangeClosed(1, CONCURRENT_USER_COUNT).boxed()
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(launchOrder, new Random(42));

        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(CONCURRENT_USER_COUNT);
        try {
            for (Long uid : launchOrder) {
                pool.submit(() -> {
                    try {
                        start.await();
                        boolean added = waitingQueueRepository.addIfAbsent(EVENT_ID, uid, uid * 1000L);
                        assertThat(added).isTrue();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(waitingQueueRepository.countWaiting(EVENT_ID)).isEqualTo(CONCURRENT_USER_COUNT);

        List<Long> poppedInOrder = new ArrayList<>();
        while (waitingQueueRepository.countWaiting(EVENT_ID) > 0) {
            List<Long> batch = waitingQueueRepository.popOldest(EVENT_ID, BATCH_SIZE);
            assertThat(batch).isNotEmpty();
            poppedInOrder.addAll(batch);
        }

        List<Long> expected = LongStream.rangeClosed(1, CONCURRENT_USER_COUNT).boxed().toList();
        assertThat(poppedInOrder).containsExactlyElementsOf(expected);
    }

    @DisplayName("TTL 초과 후 입장 토큰 조회·소비가 불가하다.")
    @Test
    void entryToken_afterTtlExpires_shouldNotBeFoundOrConsumable() throws Exception {
        long userId = 77_777L;
        String token = "ttl-verify-token";
        long ttlSeconds = 2L;
        entryTokenRepository.saveEntryToken(userId, token, ttlSeconds);

        assertThat(entryTokenRepository.findEntryToken(userId)).contains(token);

        Thread.sleep(ttlSeconds * 1000L + 1500L);

        assertThat(entryTokenRepository.findEntryToken(userId)).isEmpty();
        assertThatThrownBy(() -> orderEntryTokenService.assertValidAndConsume(userId, token))
                .isInstanceOf(CoreException.class);
    }

    @DisplayName("대기 인원이 배치 크기보다 많아도 여러 틱으로 모두 방출·토큰 발급된다.")
    @Test
    void scheduler_shouldDrainQueueLargerThanBatchAcrossMultipleTicks() {
        int total = 50;
        for (long uid = 1; uid <= total; uid++) {
            assertThat(waitingQueueRepository.addIfAbsent(EVENT_ID, uid, uid)).isTrue();
        }
        assertThat(waitingQueueRepository.countWaiting(EVENT_ID)).isEqualTo(total);

        int releasedSum = 0;
        int ticks = 0;
        while (waitingQueueRepository.countWaiting(EVENT_ID) > 0 && ticks < 10) {
            EntrySchedulerService.ReleaseResult result = entrySchedulerService.releaseEntries(
                    EVENT_ID,
                    BATCH_SIZE,
                    300L,
                    5L,
                    LOCK_KEY,
                    HEARTBEAT_KEY,
                    35L
            );
            assertThat(result.lockAcquired()).isTrue();
            releasedSum += result.releasedCount();
            ticks++;
        }

        assertThat(waitingQueueRepository.countWaiting(EVENT_ID)).isZero();
        assertThat(releasedSum).isEqualTo(total);

        for (long uid = 1; uid <= total; uid++) {
            assertThat(entryTokenRepository.findEntryToken(uid)).isPresent();
        }
    }
}
