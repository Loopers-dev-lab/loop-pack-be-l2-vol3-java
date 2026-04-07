package com.loopers.application.queue;

import com.loopers.domain.queue.QueueService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 대기열 토큰 검증 — Application Layer
 *
 * 서킷 브레이커 + 폴백 로직을 인터셉터(Interfaces)에서 분리하여
 * 비즈니스 판단(Redis 장애 시 제한적 허용)을 Application Layer에서 관리한다.
 *
 * @CircuitBreaker 어노테이션 사용 가능:
 *   - @Component이므로 Spring AOP Proxy를 거침
 *   - application.yml의 resilience4j.circuitbreaker.instances.queueRedis 설정 공유
 *
 * Fail-Open 전략:
 *   - Redis 장애 시 토큰 검증을 건너뛰되, RateLimiter로 초당 요청 수를 제한
 *   - 이커머스는 가용성(매출) 우선 — 완전 차단보다 제한적 허용
 *   - RateLimiter 제한 = 배치 크기(30건/초)와 동일 → DB 보호
 */
@Component
public class QueueTokenValidator {

    private static final Logger log = LoggerFactory.getLogger(QueueTokenValidator.class);

    private final QueueService queueService;
    private final RateLimiter rateLimiter;

    public QueueTokenValidator(QueueService queueService) {
        this.queueService = queueService;

        this.rateLimiter = RateLimiter.of("queueFallback", RateLimiterConfig.custom()
                .limitForPeriod(30)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build());
    }

    /**
     * 대기열 토큰이 유효한지 검증한다.
     *
     * Redis 정상 시: hasValidToken() 결과 반환
     * Redis 장애 시: CB가 OPEN → fallbackValidateToken() 호출
     */
    @CircuitBreaker(name = "queueRedis", fallbackMethod = "fallbackValidateToken")
    public boolean validateToken(Long userId) {
        return queueService.hasValidToken(userId);
    }

    /**
     * Redis 장애 시 Fallback — 토큰 검증 건너뛰되, RateLimiter로 DB 보호
     *
     * - RateLimiter가 초당 30건(배치 크기)까지만 허용
     * - 초과 시 false 반환 → 인터셉터에서 거절 처리
     * - 공정성은 깨지지만, 매출 유지 + DB 보호
     */
    private boolean fallbackValidateToken(Long userId, Throwable t) {
        if (rateLimiter.acquirePermission()) {
            log.warn("Redis 장애 Fallback — Rate Limit 내 허용: userId={}, cause={}",
                    userId, t.getMessage());
            return true;
        }

        log.warn("Redis 장애 Fallback — Rate Limit 초과 거부: userId={}, cause={}",
                userId, t.getMessage());
        return false;
    }
}
