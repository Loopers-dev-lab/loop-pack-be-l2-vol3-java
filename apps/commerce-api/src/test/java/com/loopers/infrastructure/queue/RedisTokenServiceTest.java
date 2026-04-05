package com.loopers.infrastructure.queue;

import com.loopers.application.queue.TokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisTokenServiceTest {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String TOKEN_KEY_PREFIX = "entry-token:";

    @AfterEach
    void tearDown() {
        redisTemplate.delete(TOKEN_KEY_PREFIX + "1");
        redisTemplate.delete(TOKEN_KEY_PREFIX + "2");
    }

    @Test
    void 토큰을_발급하면_검증에_성공한다() {
        tokenService.issue(1L);

        assertThat(tokenService.validate(1L)).isTrue();
    }

    @Test
    void 발급하지_않은_토큰은_검증에_실패한다() {
        assertThat(tokenService.validate(999L)).isFalse();
    }

    @Test
    void 토큰을_삭제하면_검증에_실패한다() {
        tokenService.issue(1L);
        tokenService.delete(1L);

        assertThat(tokenService.validate(1L)).isFalse();
    }

    @Test
    void 서로_다른_유저는_독립적인_토큰을_갖는다() {
        tokenService.issue(1L);

        assertThat(tokenService.validate(1L)).isTrue();
        assertThat(tokenService.validate(2L)).isFalse();
    }

    @Test
    void TTL이_만료되면_토큰_검증에_실패한다() throws InterruptedException {
        // 1초 TTL로 직접 설정 (서비스의 TTL 설정과 무관하게 동작 검증)
        redisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + "2", "1", Duration.ofSeconds(1));
        assertThat(tokenService.validate(2L)).isTrue();

        Thread.sleep(2000);

        assertThat(tokenService.validate(2L)).isFalse();
    }
}
