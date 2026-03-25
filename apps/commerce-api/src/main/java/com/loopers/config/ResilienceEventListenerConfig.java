package com.loopers.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Resilience4j 이벤트 리스너.
 *
 * CircuitBreaker와 Retry의 상태 변화를 로그로 관찰하기 위한 설정.
 * 이 로그를 통해:
 *  - CB가 언제, 어떤 조건에서 OPEN/HALF_OPEN/CLOSED 전이되는지 파악
 *  - Retry가 실제로 몇 회 재시도하는지 파악
 *  - Aspect 순서가 올바른지 (CB가 Retry의 최종 결과만 기록하는지) 간접 확인 가능
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ResilienceEventListenerConfig {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    @EventListener(ApplicationStartedEvent.class)
    public void registerCircuitBreakerEventListeners() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(cb -> {

            // CB 상태 전이: CLOSED → OPEN, OPEN → HALF_OPEN, HALF_OPEN → CLOSED/OPEN
            cb.getEventPublisher().onStateTransition(event ->
                    log.info("[CB] 상태 전이 | name={} | {} → {}",
                            cb.getName(),
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState()));

            // CB OPEN 상태에서 요청이 차단될 때
            cb.getEventPublisher().onCallNotPermitted(event ->
                    log.warn("[CB] 요청 차단 (OPEN 상태) | name={}", cb.getName()));

            // CB가 실패를 기록할 때 (= Retry 최종 실패 후 CB에 보고된 시점)
            cb.getEventPublisher().onError(event ->
                    log.warn("[CB] 실패 기록 | name={} | duration={}ms | error={} | failureRate={}%",
                            cb.getName(),
                            event.getElapsedDuration().toMillis(),
                            event.getThrowable().getClass().getSimpleName(),
                            String.format("%.0f", cb.getMetrics().getFailureRate())));

            // CB가 성공을 기록할 때
            cb.getEventPublisher().onSuccess(event ->
                    log.debug("[CB] 성공 기록 | name={} | duration={}ms",
                            cb.getName(),
                            event.getElapsedDuration().toMillis()));
        });
    }

    @EventListener(ApplicationStartedEvent.class)
    public void registerRetryEventListeners() {
        retryRegistry.getAllRetries().forEach(retry -> {

            // Retry가 재시도를 시작할 때 (1차 실패 후 재시도 시도)
            retry.getEventPublisher().onRetry(event ->
                    log.warn("[Retry] 재시도 | name={} | attempt={} | error={}",
                            retry.getName(),
                            event.getNumberOfRetryAttempts(),
                            event.getLastThrowable().getClass().getSimpleName()));

            // Retry가 모든 시도를 소진하고 최종 실패할 때
            retry.getEventPublisher().onError(event ->
                    log.warn("[Retry] 최종 실패 (재시도 소진) | name={} | attempts={} | error={}",
                            retry.getName(),
                            event.getNumberOfRetryAttempts(),
                            event.getLastThrowable().getClass().getSimpleName()));
        });
    }
}
