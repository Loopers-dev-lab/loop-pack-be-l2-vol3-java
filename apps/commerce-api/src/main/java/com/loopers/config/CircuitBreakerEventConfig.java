package com.loopers.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Slf4j
@RequiredArgsConstructor
@Configuration
public class CircuitBreakerEventConfig {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @PostConstruct
    public void registerEventListeners() {
        CircuitBreaker pgCircuit = circuitBreakerRegistry.circuitBreaker("pgCircuit");

        pgCircuit.getEventPublisher()
            .onStateTransition(event ->
                log.info("[서킷 브레이커 상태 전이] {} → {}",
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()))
            .onFailureRateExceeded(event ->
                log.warn("[서킷 브레이커 실패율 초과] 실패율={}%", event.getFailureRate()))
            .onSlowCallRateExceeded(event ->
                log.warn("[서킷 브레이커 느린 호출 비율 초과] 비율={}%", event.getSlowCallRate()))
            .onCallNotPermitted(event ->
                log.warn("[서킷 브레이커 호출 차단] 서킷이 Open 상태입니다."));
    }
}
