package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.SchedulerLockRepository;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

    private static final String EVENT_ID = "default";

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

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

    @DisplayName("entry token 저장/조회/삭제가 동작한다.")
    @Test
    void entryToken_saveFindDelete_shouldWork() {
        entryTokenRepository.saveEntryToken(1L, "token-1", 300L);

        assertThat(entryTokenRepository.findEntryToken(1L)).contains("token-1");

        entryTokenRepository.deleteEntryToken(1L);

        assertThat(entryTokenRepository.findEntryToken(1L)).isEmpty();
    }

    @DisplayName("scheduler lock은 setnx처럼 1회만 획득된다.")
    @Test
    void schedulerLock_tryAcquireLock_shouldBehaveLikeSetNx() {
        boolean first = schedulerLockRepository.tryAcquireLock("queue:scheduler:lock", "lock-1", 5L);
        boolean second = schedulerLockRepository.tryAcquireLock("queue:scheduler:lock", "lock-2", 5L);

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
}

