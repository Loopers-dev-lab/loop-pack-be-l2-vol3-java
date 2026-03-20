package com.loopers.infrastructure.resilience;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 결제 요청 Rate Limiter AOP.
 *
 * <p>실행 순서: SlidingWindowRateLimiter → Retry → CircuitBreaker → PgClient</p>
 *
 * <p>Rate Limiter 거부는 CB에 기록하지 않는다:
 * 트래픽 초과 ≠ PG 장애. CB에 기록하면 트래픽만 많아도 CB Open → 오작동.</p>
 *
 * @see <a href="05-payment-resilience.md §7.5">실행 순서</a>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class PaymentRateLimiterInterceptor {

    private final SlidingWindowRateLimiter paymentRateLimiter;

    @Around("execution(* com.loopers.application.payment.PaymentFacade.requestPayment(..))")
    public Object checkRateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!paymentRateLimiter.tryAcquire()) {
            log.warn("결제 요청 Rate Limit 초과 — 429 응답");
            throw new CoreException(ErrorType.TOO_MANY_REQUESTS,
                "결제 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
        }
        return joinPoint.proceed();
    }
}
