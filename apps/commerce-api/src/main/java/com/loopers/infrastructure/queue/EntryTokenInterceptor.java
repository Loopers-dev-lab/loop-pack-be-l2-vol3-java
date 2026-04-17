package com.loopers.infrastructure.queue;

import com.loopers.domain.member.Member;
import com.loopers.infrastructure.redis.EntryTokenRedisRepository;
import com.loopers.infrastructure.resilience.SlidingWindowRateLimiter;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 대기열 입장 토큰 검증 AOP.
 *
 * <p>{@code @RequireEntryToken} 어노테이션이 붙은 메서드 실행 전 토큰 존재 여부를 확인한다.
 * 성공 시에만 토큰을 소비하여 예외 발생 시 재시도가 가능하도록 한다.</p>
 *
 * <p>Redis 장애 시 로컬 Rate Limiter로 전환하여 DB 커넥션 풀을 보호한다.
 * 정상 모드와 동일한 80 req/sec 제한으로, Redis 없이도 트래픽 제어를 유지한다.</p>
 *
 * @see com.loopers.support.auth.RequireEntryToken
 */
@Slf4j
@Aspect
@Component
public class EntryTokenInterceptor {

    private static final long ERROR_LOG_INTERVAL_MILLIS = 10_000;

    private final EntryTokenRedisRepository entryTokenRedisRepository;
    private final SlidingWindowRateLimiter fallbackRateLimiter;
    private final Counter fallbackCounter;
    private final AtomicLong lastFallbackErrorLogTime = new AtomicLong(0);

    public EntryTokenInterceptor(
        EntryTokenRedisRepository entryTokenRedisRepository,
        @Qualifier("queueFallbackRateLimiter") SlidingWindowRateLimiter fallbackRateLimiter,
        MeterRegistry meterRegistry
    ) {
        this.entryTokenRedisRepository = entryTokenRedisRepository;
        this.fallbackRateLimiter = fallbackRateLimiter;
        this.fallbackCounter = Counter.builder("queue.token.fallback")
            .description("Redis 장애 시 fallback 발동 횟수")
            .register(meterRegistry);
    }

    @Around("@annotation(com.loopers.support.auth.RequireEntryToken)")
    public Object validateEntryToken(ProceedingJoinPoint joinPoint) throws Throwable {
        Long memberId = extractMemberIdFromArgs(joinPoint);

        try {
            if (!entryTokenRedisRepository.exists(memberId)) {
                log.warn("입장 토큰 없음 — 주문 거부: memberId={}", memberId);
                throw new CoreException(ErrorType.FORBIDDEN, "대기열 입장 토큰이 없습니다.");
            }
        } catch (CoreException e) {
            throw e;
        } catch (Exception e) {
            return handleRedisFallback(joinPoint, memberId, e);
        }

        Object result = joinPoint.proceed();

        try {
            entryTokenRedisRepository.consume(memberId);
        } catch (Exception e) {
            throttledWarn(e, memberId);
            // 토큰은 TTL로 자동 만료되므로 소비 실패는 무시
        }

        return result;
    }

    private Object handleRedisFallback(ProceedingJoinPoint joinPoint, Long memberId, Exception cause) throws Throwable {
        fallbackCounter.increment();
        throttledWarn(cause, memberId);

        if (!fallbackRateLimiter.tryAcquire()) {
            throw new CoreException(ErrorType.TOO_MANY_REQUESTS, "시스템이 일시적으로 혼잡합니다.");
        }

        Object result = joinPoint.proceed();
        // fallback 모드에서는 토큰 소비를 시도하지 않음 (Redis 장애 상태)
        return result;
    }

    private void throttledWarn(Exception e, Long memberId) {
        long now = System.currentTimeMillis();
        long last = lastFallbackErrorLogTime.get();
        if (now - last >= ERROR_LOG_INTERVAL_MILLIS && lastFallbackErrorLogTime.compareAndSet(last, now)) {
            log.warn("Redis 장애 — fallback 모드: memberId={}, error={}", memberId, e.getMessage());
        }
    }

    private Long extractMemberIdFromArgs(ProceedingJoinPoint joinPoint) {
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof Member member) {
                return member.getId();
            }
        }
        throw new CoreException(ErrorType.INTERNAL_ERROR, "Member 인자를 찾을 수 없습니다.");
    }
}
