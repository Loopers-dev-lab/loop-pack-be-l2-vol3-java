package com.loopers.infrastructure.resilience;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentRateLimiterConfig {

    /**
     * 결제 요청 전용 Rate Limiter — 50 req/sec.
     * PG 계약 TPS를 정확히 지키기 위해 Sliding Window Counter 사용.
     */
    @Bean
    public SlidingWindowRateLimiter paymentRateLimiter() {
        return new SlidingWindowRateLimiter(50, 1000);
    }
}
