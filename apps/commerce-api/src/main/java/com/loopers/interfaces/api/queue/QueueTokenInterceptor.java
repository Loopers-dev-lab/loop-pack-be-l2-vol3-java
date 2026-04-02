package com.loopers.interfaces.api.queue;

import com.loopers.domain.queue.QueueProperties;
import com.loopers.domain.queue.QueueService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.QueueErrorType;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * 대기열 토큰 검증 Interceptor
 *
 * 주문 API 경로(/api/v1/orders)에 대해 대기열 토큰을 검증한다.
 * 대기열이 꺼져 있으면 토큰 검증을 건너뛴다 (Feature Flag).
 *
 * Redis 장애 대응 (Fail-Open + Rate Limit):
 * - CircuitBreaker가 Redis 연결 실패를 감지하면 OPEN 상태로 전환
 * - OPEN 상태에서는 토큰 검증을 건너뛰되, RateLimiter로 초당 요청 수를 제한
 * - Redis 복구 시 HALF_OPEN → CLOSED로 자동 전환
 *
 * 설계 결정:
 * - Interceptor를 선택한 이유: 주문 도메인이 큐의 존재를 모르게 하기 위해
 * - Fail-Open: 이커머스는 가용성(매출) 우선 — 완전 차단보다 제한적 허용
 * - Rate Limit: DB 보호를 위해 초당 30건(배치 크기와 동일)으로 제한
 */
@Component
public class QueueTokenInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(QueueTokenInterceptor.class);

    private final QueueService queueService;
    private final QueueProperties queueProperties;
    private final CircuitBreaker circuitBreaker;
    private final RateLimiter rateLimiter;

    public QueueTokenInterceptor(QueueService queueService, QueueProperties queueProperties) {
        this.queueService = queueService;
        this.queueProperties = queueProperties;

        this.circuitBreaker = CircuitBreaker.of("queueRedis", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(5)
                .minimumNumberOfCalls(3)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(2)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(RedisConnectionFailureException.class)
                .build());

        this.rateLimiter = RateLimiter.of("queueFallback", RateLimiterConfig.custom()
                .limitForPeriod(30)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build());
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!queueProperties.isEnabled()) {
            return true;
        }

        Long userId = extractUserId(request);
        if (userId == null) {
            return true;
        }

        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            return handleFallback(userId, request);
        }

        try {
            boolean hasToken = circuitBreaker.executeSupplier(() -> queueService.hasValidToken(userId));
            if (!hasToken) {
                log.warn("대기열 토큰 없음: userId={}, path={}", userId, request.getRequestURI());
                throw new CoreException(QueueErrorType.QUEUE_TOKEN_REQUIRED);
            }
            return true;
        } catch (RedisConnectionFailureException e) {
            log.error("Redis 연결 실패 — Fallback 모드 진입: userId={}", userId, e);
            return handleFallback(userId, request);
        }
    }

    /**
     * Redis 장애 시 Fallback: 토큰 검증 건너뛰되, Rate Limit으로 DB 보호
     *
     * - RateLimiter가 초당 30건(배치 크기)까지만 허용
     * - 초과 시 503 응답 → 사용자에게 "잠시 후 다시 시도" 안내
     * - 공정성은 깨지지만, 매출 유지 + DB 보호
     */
    private boolean handleFallback(Long userId, HttpServletRequest request) {
        if (rateLimiter.acquirePermission()) {
            log.warn("Redis 장애 Fallback — Rate Limit 내 허용: userId={}, path={}",
                    userId, request.getRequestURI());
            return true;
        }

        log.warn("Redis 장애 Fallback — Rate Limit 초과 거부: userId={}, path={}",
                userId, request.getRequestURI());
        throw new CoreException(QueueErrorType.QUEUE_TOKEN_REQUIRED,
                "현재 시스템이 혼잡합니다. 잠시 후 다시 시도해주세요.");
    }

    private Long extractUserId(HttpServletRequest request) {
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader == null || userIdHeader.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userIdHeader);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
