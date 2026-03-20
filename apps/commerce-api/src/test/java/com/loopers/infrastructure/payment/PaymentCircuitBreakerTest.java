package com.loopers.infrastructure.payment;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;

import java.time.Duration;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 3-3: CircuitBreaker 설정 검증 테스트.
 * <p>
 * PG 연속 실패 시 서킷이 OPEN되어 즉시 실패하고,
 * 대기 후 HALF_OPEN → CLOSED로 복귀하는 동작을 검증한다.
 * </p>
 */
@DisplayName("Phase 3-3: PG CircuitBreaker 설정 테스트")
class PaymentCircuitBreakerTest {

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(60)
                .slowCallDurationThreshold(Duration.ofSeconds(1))
                .slowCallRateThreshold(60)
                .waitDurationInOpenState(Duration.ofMillis(500)) // 테스트용 짧은 대기
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();

        circuitBreaker = CircuitBreakerRegistry.of(config).circuitBreaker("pgCircuit");
    }

    @Test
    @DisplayName("PG 연속 실패 시 서킷이 OPEN된다")
    void circuitBreaker_WithConsecutiveFailures_ShouldOpen() {
        // 5건 연속 실패 (minimumNumberOfCalls=5, failureRateThreshold=60)
        for (int i = 0; i < 5; i++) {
            try {
                CircuitBreaker.decorateRunnable(circuitBreaker, () -> {
                    throw new HttpServerErrorException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
                }).run();
            } catch (Exception ignored) {
            }
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("서킷 OPEN 후 즉시 실패한다 (CallNotPermittedException)")
    void circuitBreaker_WhenOpen_ShouldFailImmediately() {
        // 서킷을 강제로 OPEN
        circuitBreaker.transitionToOpenState();

        Supplier<String> decorated = CircuitBreaker.decorateSupplier(circuitBreaker, () -> "SUCCESS");
        assertThatThrownBy(decorated::get)
                .isInstanceOf(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class);
        // 실제 PG 호출(100~500ms) 없이 즉시 예외가 발생함을 검증
        // (JIT 초기화 오버헤드로 ms 단위 검증은 불안정하므로 예외 타입으로 검증)
    }

    @Test
    @DisplayName("서킷 OPEN 대기 후 HALF_OPEN으로 전이한다")
    void circuitBreaker_AfterWaitDuration_ShouldTransitionToHalfOpen() throws InterruptedException {
        // 5건 연속 실패로 OPEN
        for (int i = 0; i < 5; i++) {
            try {
                CircuitBreaker.decorateRunnable(circuitBreaker, () -> {
                    throw new HttpServerErrorException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
                }).run();
            } catch (Exception ignored) {
            }
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // waitDurationInOpenState (500ms) 대기
        Thread.sleep(600);

        // HALF_OPEN 전이를 위해 요청 시도
        try {
            CircuitBreaker.decorateRunnable(circuitBreaker, () -> {}).run();
        } catch (Exception ignored) {
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
    }

    @Test
    @DisplayName("PG 복구 후 서킷이 CLOSED로 복귀한다")
    void circuitBreaker_AfterRecovery_ShouldTransitionToClosed() throws InterruptedException {
        // 5건 연속 실패로 OPEN
        for (int i = 0; i < 5; i++) {
            try {
                CircuitBreaker.decorateRunnable(circuitBreaker, () -> {
                    throw new HttpServerErrorException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
                }).run();
            } catch (Exception ignored) {
            }
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // waitDuration 대기 후 HALF_OPEN
        Thread.sleep(600);

        // HALF_OPEN에서 3건(permittedNumberOfCallsInHalfOpenState) 성공
        for (int i = 0; i < 3; i++) {
            try {
                CircuitBreaker.decorateSupplier(circuitBreaker, () -> "SUCCESS").get();
            } catch (Exception ignored) {
            }
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("서킷 OPEN 상태에서의 응답 시간은 수 ms 이내이다")
    void circuitBreaker_WhenOpen_ResponseTimeShouldBeMinimal() {
        circuitBreaker.transitionToOpenState();

        long totalElapsed = 0;
        int attempts = 10;

        for (int i = 0; i < attempts; i++) {
            long start = System.nanoTime();
            try {
                CircuitBreaker.decorateSupplier(circuitBreaker, () -> "SUCCESS").get();
            } catch (Exception ignored) {
            }
            totalElapsed += (System.nanoTime() - start);
        }

        long avgMs = totalElapsed / attempts / 1_000_000;
        assertThat(avgMs).isLessThan(10); // 평균 10ms 이내
    }
}
