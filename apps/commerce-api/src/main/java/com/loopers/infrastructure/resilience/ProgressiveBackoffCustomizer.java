package com.loopers.infrastructure.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Progressive Backoff — CB Open 반복 시 대기 시간을 점진적으로 증가시킨다.
 *
 * <pre>
 * 1차 Open: 5초 → Half-Open
 * 실패 → 2차 Open: 10초
 * 실패 → 3차 Open: 20초
 * 실패 → 4차 Open: 40초
 * 실패 → 5차+ Open: 60초 (cap)
 * 성공 (Closed) → 카운트 리셋
 * </pre>
 *
 * <p>한계: Resilience4j의 wait-duration-in-open-state는 정적 설정이다.
 * 현재 구현은 이벤트 로깅 + 대기 시간 계산을 제공하며,
 * PgHealthChecker 스케줄러가 이 대기 시간을 참조하여 Health Check 간격을 조정한다.</p>
 *
 * @see <a href="06-resilience-review.md §15.2">Progressive Backoff 설계</a>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgressiveBackoffCustomizer {

    private static final long BASE_WAIT_SECONDS = 5;
    private static final long MAX_WAIT_SECONDS = 60;

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final Map<String, AtomicInteger> openCountMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void customize() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(this::registerEventListener);

        // 새로 등록되는 CB에도 리스너 추가
        circuitBreakerRegistry.getEventPublisher()
            .onEntryAdded(event -> registerEventListener(event.getAddedEntry()));
    }

    private void registerEventListener(CircuitBreaker cb) {
        cb.getEventPublisher()
            .onStateTransition(event -> handleTransition(cb.getName(), event));
    }

    private void handleTransition(String cbName, CircuitBreakerOnStateTransitionEvent event) {
        CircuitBreaker.StateTransition transition = event.getStateTransition();

        switch (transition) {
            case HALF_OPEN_TO_OPEN -> {
                // Half-Open 실패 → 다시 Open — 카운트 증가
                int count = openCountMap.computeIfAbsent(cbName, k -> new AtomicInteger(0))
                    .incrementAndGet();
                Duration nextWait = calculateWaitDuration(count);
                log.warn("CB [{}] Half-Open → Open ({}회차), 다음 대기: {}초",
                    cbName, count, nextWait.getSeconds());
            }
            case HALF_OPEN_TO_CLOSED -> {
                // 복구 성공 → 카운트 리셋
                openCountMap.computeIfAbsent(cbName, k -> new AtomicInteger(0)).set(0);
                log.info("CB [{}] 복구 완료 (Closed), Progressive Backoff 카운트 리셋", cbName);
            }
            case CLOSED_TO_OPEN -> {
                log.warn("CB [{}] Closed → Open", cbName);
            }
            default -> {}
        }
    }

    /**
     * CB의 현재 Progressive Backoff 대기 시간을 반환한다.
     */
    public Duration getWaitDuration(String cbName) {
        int count = openCountMap.getOrDefault(cbName, new AtomicInteger(0)).get();
        return calculateWaitDuration(count);
    }

    /**
     * CB의 현재 Open 카운트를 반환한다.
     */
    public int getOpenCount(String cbName) {
        return openCountMap.getOrDefault(cbName, new AtomicInteger(0)).get();
    }

    private Duration calculateWaitDuration(int openCount) {
        long seconds = Math.min(BASE_WAIT_SECONDS * (1L << openCount), MAX_WAIT_SECONDS);
        return Duration.ofSeconds(seconds);
    }
}
