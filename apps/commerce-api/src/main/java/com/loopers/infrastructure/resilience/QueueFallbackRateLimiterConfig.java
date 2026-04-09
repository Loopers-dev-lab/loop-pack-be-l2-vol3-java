package com.loopers.infrastructure.resilience;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueueFallbackRateLimiterConfig {

    /**
     * Redis 장애 시 EntryTokenInterceptor가 사용하는 로컬 Rate Limiter.
     *
     * <p>Rate Limit = 80 req/sec (정상 모드 입장 속도와 동일).
     * Redis 없이도 DB 커넥션 풀 보호를 유지한다.</p>
     *
     * @see com.loopers.infrastructure.queue.EntryTokenInterceptor
     */
    @Bean
    public SlidingWindowRateLimiter queueFallbackRateLimiter(
        @Value("${queue.fallback.rate-limit:80}") int rateLimit
    ) {
        return new SlidingWindowRateLimiter(rateLimit, 1000);
    }
}
