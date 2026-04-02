package com.loopers.domain.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("토큰 TTL 만료 테스트")
class TokenExpirationTest {

    @Autowired
    private TokenRepository tokenRepository;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("토큰 TTL이 만료되면 조회 시 null을 반환한다")
    void token_expiresAfterTtl() throws InterruptedException {
        // Given — TTL 1초로 토큰 발급
        Long userId = 1L;
        tokenRepository.saveToken(userId, "test-token", 1);

        // 발급 직후에는 조회 가능
        assertThat(tokenRepository.getToken(userId)).isEqualTo("test-token");

        // When — 2초 대기 (TTL 초과)
        Thread.sleep(2000);

        // Then — 만료되어 null
        assertThat(tokenRepository.getToken(userId)).isNull();
    }

    @Test
    @DisplayName("만료된 토큰으로 주문 시 검증 실패한다")
    void expiredToken_validationFails() throws InterruptedException {
        // Given — TTL 1초로 토큰 발급
        Long userId = 1L;
        tokenRepository.saveToken(userId, "test-token", 1);

        // When — 2초 대기 (TTL 초과)
        Thread.sleep(2000);

        // Then — QueueService에서 검증 시 예외
        QueueService queueService = new QueueService(
                new QueueRepository() {
                    public boolean enter(Long u, double s) { return false; }
                    public Long getPosition(Long u) { return null; }
                    public long getTotalSize() { return 0; }
                    public java.util.Set<String> pollBatch(int c) { return java.util.Collections.emptySet(); }
                },
                tokenRepository
        );

        org.junit.jupiter.api.Assertions.assertThrows(
                com.loopers.support.error.CoreException.class,
                () -> queueService.validateToken(userId, "test-token")
        );
    }
}
