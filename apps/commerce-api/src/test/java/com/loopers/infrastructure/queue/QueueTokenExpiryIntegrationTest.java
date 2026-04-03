package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueTokenRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@ActiveProfiles("test")
class QueueTokenExpiryIntegrationTest {

    @Autowired
    private QueueTokenRepository queueTokenRepository;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("TTL이 만료되면 토큰이 자동으로 삭제되어 조회할 수 없다")
    void TTL_만료_시_토큰이_자동_삭제된다() throws InterruptedException {
        // given
        String eventId = "event-ttl";
        Long userId = 1L;
        String token = "expire-test-token";
        long shortTtl = 1;

        queueTokenRepository.issueToken(eventId, userId, token, shortTtl);

        // when
        Thread.sleep(1500);
        Optional<String> result = queueTokenRepository.getToken(eventId, userId);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("TTL이 만료되면 TTL 조회 시 음수를 반환한다")
    void TTL_만료_시_TTL_조회가_음수를_반환한다() throws InterruptedException {
        // given
        String eventId = "event-ttl";
        Long userId = 2L;
        long shortTtl = 1;

        queueTokenRepository.issueToken(eventId, userId, "token-2", shortTtl);

        // when
        Thread.sleep(1500);
        long ttl = queueTokenRepository.getTokenTtl(eventId, userId);

        // then
        assertThat(ttl).isNegative();
    }
}
