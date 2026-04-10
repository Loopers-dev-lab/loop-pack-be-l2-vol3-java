package com.loopers.infrastructure.collector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class RedisProductSoldContributionLimiterTest {

    @Test
    @DisplayName("상한이 0 이하면 항상 허용하고 Redis를 호출하지 않는다.")
    void allowContribution_whenCapDisabled_shouldAlwaysAllow() {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        RedisProductSoldContributionLimiter limiter =
                new RedisProductSoldContributionLimiter(redisTemplate, 0, 2);

        boolean allowed =
                limiter.allowContribution("buyer-1", 101L, 3L, Instant.parse("2026-03-26T00:00:00Z"));

        assertThat(allowed).isTrue();
        verify(redisTemplate, never()).execute(any(DefaultRedisScript.class), anyList(), any());
    }

    @Test
    @DisplayName("Lua가 1을 반환하면 허용한다.")
    void allowContribution_whenScriptReturnsOne_shouldAllow() {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(1L);
        RedisProductSoldContributionLimiter limiter =
                new RedisProductSoldContributionLimiter(redisTemplate, 10, 2);

        boolean allowed =
                limiter.allowContribution("buyer-1", 101L, 3L, Instant.parse("2026-03-26T00:00:00Z"));

        assertThat(allowed).isTrue();
        verify(redisTemplate)
                .execute(
                        any(DefaultRedisScript.class),
                        eq(List.of("collector:sold-cap:20260326:buyer-1:101")),
                        eq("3"),
                        eq("10"),
                        eq(String.valueOf(2L * 86400L)));
    }

    @Test
    @DisplayName("Lua가 0을 반환하면 차단한다.")
    void allowContribution_whenScriptReturnsZero_shouldDeny() {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(0L);
        RedisProductSoldContributionLimiter limiter =
                new RedisProductSoldContributionLimiter(redisTemplate, 5, 2);

        boolean allowed =
                limiter.allowContribution("buyer-1", 101L, 9L, Instant.parse("2026-03-26T00:00:00Z"));

        assertThat(allowed).isFalse();
    }
}
