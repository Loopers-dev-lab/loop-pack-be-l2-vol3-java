package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.EntrySchedulerService;
import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.JoinQueueResult;
import com.loopers.domain.queue.QueuePositionSnapshot;
import com.loopers.domain.queue.SchedulerLockRepository;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.domain.queue.WaitingQueueService;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
class QueueRedisInfrastructureIntegrationTest {

    /** 스케줄러 기본 event-id(default)와 겹치면 틱이 ZSET을 비울 수 있어 분리한다. */
    private static final String EVENT_ID = "redis-integration-queue";

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

    @Autowired
    private WaitingQueueService waitingQueueService;

    @Autowired
    private EntrySchedulerService entrySchedulerService;

    @Autowired
    private EntryTokenRepository entryTokenRepository;

    @Autowired
    private SchedulerLockRepository schedulerLockRepository;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("findPositionSnapshot은 ZRANK·ZCARD를 일치시킨다.")
    @Test
    void findPositionSnapshot_shouldReturnRankAndCountAtomically() {
        waitingQueueRepository.addIfAbsent(EVENT_ID, 30L, 3000L);
        waitingQueueRepository.addIfAbsent(EVENT_ID, 10L, 1000L);

        var snap = waitingQueueRepository.findPositionSnapshot(EVENT_ID, 10L);

        assertThat(snap).contains(new QueuePositionSnapshot(0L, 2L));
    }

    @DisplayName("popOldest는 score가 작은 순서대로 userId를 꺼낸다.")
    @Test
    void popOldest_shouldPopUsersInScoreOrder() {
        waitingQueueRepository.addIfAbsent(EVENT_ID, 30L, 3000L);
        waitingQueueRepository.addIfAbsent(EVENT_ID, 10L, 1000L);
        waitingQueueRepository.addIfAbsent(EVENT_ID, 20L, 2000L);

        List<Long> popped = waitingQueueRepository.popOldest(EVENT_ID, 2);

        assertThat(popped).containsExactly(10L, 20L);
        assertThat(waitingQueueRepository.countWaiting(EVENT_ID)).isEqualTo(1L);
    }

    /**
     * TC-R1-1: 동일 score일 때 Redis ZSET은 멤버 문자열 lex 순으로 정렬한다.
     * (예: "10" &lt; "2" &lt; "3" — 숫자 크기와 다름)
     */
    @DisplayName("동일 score면 pop 순서는 멤버 lex 순(문서화된 Redis 동작)")
    @Test
    void sameScore_popOldest_shouldFollowRedisLexMemberOrder() {
        String eventId = EVENT_ID + "-tie";
        long sameScore = 9999L;
        waitingQueueRepository.addIfAbsent(eventId, 2L, sameScore);
        waitingQueueRepository.addIfAbsent(eventId, 10L, sameScore);
        waitingQueueRepository.addIfAbsent(eventId, 3L, sameScore);

        List<Long> popped = waitingQueueRepository.popOldest(eventId, 3);

        assertThat(popped).containsExactly(10L, 2L, 3L);
    }

    /** TC-R4-1: Kafka 복구 경로 재처리 시에도 ZSET에는 유저당 멤버 하나(멱등). */
    @DisplayName("joinQueueFromRecovery 동일 유저·score 두 번 → 대기 1명·순번 유지")
    @Test
    void joinQueueFromRecovery_twiceSameUser_shouldRemainSingleMember() {
        String eventId = EVENT_ID + "-recovery-idem";
        long score = 42_000L;

        JoinQueueResult first = waitingQueueService.joinQueueFromRecovery(eventId, 100L, score);
        JoinQueueResult second = waitingQueueService.joinQueueFromRecovery(eventId, 100L, score);

        assertThat(waitingQueueRepository.countWaiting(eventId)).isEqualTo(1L);
        assertThat(first.position()).isEqualTo(second.position());
        assertThat(first.totalWaiting()).isEqualTo(second.totalWaiting());
    }

    @DisplayName("entry token 저장/조회/삭제가 동작한다.")
    @Test
    void entryToken_saveFindDelete_shouldWork() {
        entryTokenRepository.saveEntryToken(1L, "token-1", 300L);

        assertThat(entryTokenRepository.findEntryToken(1L)).contains("token-1");

        entryTokenRepository.deleteEntryToken(1L);

        assertThat(entryTokenRepository.findEntryToken(1L)).isEmpty();
    }

    @DisplayName("consumeIfTokenMatches는 값이 일치할 때만 키를 제거한다.")
    @Test
    void entryToken_consumeIfTokenMatches_shouldDeleteOnlyWhenMatch() {
        entryTokenRepository.saveEntryToken(1L, "secret", 300L);

        assertThat(entryTokenRepository.consumeIfTokenMatches(1L, "wrong")).isFalse();
        assertThat(entryTokenRepository.findEntryToken(1L)).contains("secret");

        assertThat(entryTokenRepository.consumeIfTokenMatches(1L, "secret")).isTrue();
        assertThat(entryTokenRepository.findEntryToken(1L)).isEmpty();

        assertThat(entryTokenRepository.consumeIfTokenMatches(1L, "secret")).isFalse();
    }

    @DisplayName("scheduler lock은 setnx처럼 1회만 획득된다.")
    @Test
    void schedulerLock_tryAcquireLock_shouldBehaveLikeSetNx() {
        String lockKey = "queue:scheduler:lock:it";
        boolean first = schedulerLockRepository.tryAcquireLock(lockKey, "lock-1", 5L);
        boolean second = schedulerLockRepository.tryAcquireLock(lockKey, "lock-2", 5L);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    @DisplayName("heartbeat 갱신 시 redis에 값이 저장된다.")
    @Test
    void schedulerLock_updateHeartbeat_shouldStoreValue() {
        schedulerLockRepository.updateHeartbeat("queue:scheduler:heartbeat", "12345", 35L);

        String value = redisTemplate.opsForValue().get("queue:scheduler:heartbeat");
        assertThat(value).isEqualTo("12345");
    }

    /**
     * 분산 락 TTL 동안 연속 틱 시 두 번째는 락 미획득(실제 Redis, MockBean 없음).
     */
    @DisplayName("EntrySchedulerService: 락이 풀리기 전 두 번째 releaseEntries는 스킵")
    @Test
    void releaseEntries_whenLockStillHeld_secondTickSkips() {
        String lockKey = "queue:scheduler:lock:contention-it";
        String heartbeatKey = "queue:scheduler:heartbeat:contention-it";
        String eventId = EVENT_ID + "-lock-contention";

        EntrySchedulerService.ReleaseResult first = entrySchedulerService.releaseEntries(
                eventId, 18, 300L, 5L, lockKey, heartbeatKey, 35L);
        double skippedBeforeSecond = meterRegistry.counter("loopers.queue.scheduler.lock.skipped").count();
        EntrySchedulerService.ReleaseResult second = entrySchedulerService.releaseEntries(
                eventId, 18, 300L, 5L, lockKey, heartbeatKey, 35L);

        assertThat(first.lockAcquired()).isTrue();
        assertThat(second.lockAcquired()).isFalse();
        assertThat(second.releasedCount()).isZero();
        assertThat(meterRegistry.counter("loopers.queue.scheduler.lock.skipped").count())
                .isEqualTo(skippedBeforeSecond + 1.0);
    }
}

