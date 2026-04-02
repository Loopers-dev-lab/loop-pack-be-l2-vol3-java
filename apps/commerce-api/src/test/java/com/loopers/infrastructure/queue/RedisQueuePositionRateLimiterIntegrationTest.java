package com.loopers.infrastructure.queue;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    properties = {
        "spring.task.scheduling.enabled=false",
        "queue.position.rate-limit.enabled=true",
        "queue.position.rate-limit.max-requests-per-second=3",
        "queue.position.rate-limit.window-seconds=1"
    }
)
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
class RedisQueuePositionRateLimiterIntegrationTest {

    private static final String LOGIN = "ratelimituser";

    @Autowired
    private RedisQueuePositionRateLimiter rateLimiter;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("동일 loginId로 초당 상한까지 허용하고 초과 시 거부한다.")
    @Test
    void tryAcquire_sameUser_exceedsLimit_thenDeny() {
        assertThat(rateLimiter.tryAcquire(LOGIN)).isTrue();
        assertThat(rateLimiter.tryAcquire(LOGIN)).isTrue();
        assertThat(rateLimiter.tryAcquire(LOGIN)).isTrue();
        assertThat(rateLimiter.tryAcquire(LOGIN)).isFalse();
    }

    @DisplayName("서로 다른 loginId는 동일 초에 각각 상한이 적용된다.")
    @Test
    void tryAcquire_differentUsers_independentLimits() {
        assertThat(rateLimiter.tryAcquire("userA")).isTrue();
        assertThat(rateLimiter.tryAcquire("userB")).isTrue();
        assertThat(rateLimiter.tryAcquire("userA")).isTrue();
        assertThat(rateLimiter.tryAcquire("userA")).isTrue();
        assertThat(rateLimiter.tryAcquire("userA")).isFalse();
    }
}
