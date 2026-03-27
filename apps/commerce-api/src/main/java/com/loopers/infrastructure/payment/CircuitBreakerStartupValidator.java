package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class CircuitBreakerStartupValidator {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @EventListener(ApplicationReadyEvent.class)
    public void validate() {
        List<String> requiredKeys = CardType.allCircuitBreakerKeys();
        Set<String> registeredNames = circuitBreakerRegistry.getAllCircuitBreakers()
                .stream()
                .map(CircuitBreaker::getName)
                .collect(Collectors.toSet());

        List<String> missingKeys = requiredKeys.stream()
                .filter(key -> !registeredNames.contains(key))
                .toList();

        if (!missingKeys.isEmpty()) {
            throw new IllegalStateException(
                    "서킷브레이커 설정 누락! 필요한 키: " + missingKeys
                            + ", 등록된 키: " + registeredNames);
        }

        log.info("──── 서킷브레이커 Startup 검증 완료 ────");
        for (String key : requiredKeys) {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(key);
            var config = cb.getCircuitBreakerConfig();
            log.info("  {} → state={}, windowType={}, windowSize={}, failureThreshold={}%",
                    key, cb.getState(),
                    config.getSlidingWindowType(),
                    config.getSlidingWindowSize(),
                    config.getFailureRateThreshold());
        }
        log.info("───────────────────────────────────────");
    }
}
