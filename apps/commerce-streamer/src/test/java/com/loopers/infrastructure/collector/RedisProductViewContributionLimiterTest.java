package com.loopers.infrastructure.collector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisProductViewContributionLimiterTest {

    @Test
    @DisplayName("상한이 0 이하면 항상 허용한다.")
    void allowContribution_whenCapDisabled_shouldAlwaysAllow() {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        RedisProductViewContributionLimiter limiter = new RedisProductViewContributionLimiter(redisTemplate, 0, 2);

        boolean allowed = limiter.allowContribution("viewer-1", 101L, Instant.parse("2026-03-26T00:00:00Z"));

        assertThat(allowed).isTrue();
    }

    @Test
    @DisplayName("일일 상한 이내면 허용한다.")
    void allowContribution_whenWithinCap_shouldAllow() {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.increment("collector:view-cap:20260326:viewer-1:101")).thenReturn(1L);

        RedisProductViewContributionLimiter limiter = new RedisProductViewContributionLimiter(redisTemplate, 2, 2);
        boolean allowed = limiter.allowContribution("viewer-1", 101L, Instant.parse("2026-03-26T00:00:00Z"));

        assertThat(allowed).isTrue();
        verify(redisTemplate).expire("collector:view-cap:20260326:viewer-1:101", java.time.Duration.ofDays(2));
    }

    @Test
    @DisplayName("일일 상한 초과면 차단한다.")
    void allowContribution_whenOverCap_shouldDeny() {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.increment("collector:view-cap:20260326:viewer-1:101")).thenReturn(3L);

        RedisProductViewContributionLimiter limiter = new RedisProductViewContributionLimiter(redisTemplate, 2, 2);
        boolean allowed = limiter.allowContribution("viewer-1", 101L, Instant.parse("2026-03-26T00:00:00Z"));

        assertThat(allowed).isFalse();
    }
}
