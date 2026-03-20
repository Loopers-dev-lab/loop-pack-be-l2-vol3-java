package com.loopers.infrastructure.payment;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentGatewayResilienceExecutorTest {

    private PaymentGatewayResilienceExecutor paymentGatewayResilienceExecutor;
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .slowCallRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(2))
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(5)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
        circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);

        RetryConfig requestRetryConfig = RetryConfig.custom()
                .maxAttempts(1)
                .waitDuration(Duration.ofMillis(150))
                .retryExceptions(PaymentGatewayConnectionException.class)
                .build();
        RetryConfig cancelRetryConfig = RetryConfig.custom()
                .maxAttempts(1)
                .waitDuration(Duration.ofMillis(150))
                .retryExceptions(PaymentGatewayConnectionException.class)
                .build();
        RetryConfig queryRetryConfig = RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(100))
                .retryExceptions(PaymentGatewayConnectionException.class)
                .build();

        RetryRegistry retryRegistry = RetryRegistry.ofDefaults();
        retryRegistry.retry("pg-request-connection", requestRetryConfig);
        retryRegistry.retry("pg-cancel-connection", cancelRetryConfig);
        retryRegistry.retry("pg-query-connection", queryRetryConfig);

        paymentGatewayResilienceExecutor = new PaymentGatewayResilienceExecutor(circuitBreakerRegistry, retryRegistry);
    }

    @AfterEach
    void resetCircuitBreakers() {
        circuitBreakerRegistry.circuitBreaker("pg-request").reset();
        circuitBreakerRegistry.circuitBreaker("pg-cancel").reset();
        circuitBreakerRegistry.circuitBreaker("pg-query").reset();
    }

    @Test
    @DisplayName("PG request 재시도는 연결 예외에 대해서만 1회만 수행한다")
    void requestConnectionRetryConfiguredAsSingleAttempt() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> paymentGatewayResilienceExecutor.executeRequest(() -> {
            attempts.incrementAndGet();
            throw new PaymentGatewayConnectionException("connection failed", null);
        })).isInstanceOf(PaymentGatewayConnectionException.class);

        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("PG cancel 재시도는 연결 예외에 대해서만 1회만 수행한다")
    void cancelConnectionRetryConfiguredAsSingleAttempt() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> paymentGatewayResilienceExecutor.executeCancel(() -> {
            attempts.incrementAndGet();
            throw new PaymentGatewayConnectionException("connection failed", null);
        })).isInstanceOf(PaymentGatewayConnectionException.class);

        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("PG query 재시도는 연결 예외에 대해서만 2회까지 수행한다")
    void queryConnectionRetryConfiguredAsTwoAttempts() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> paymentGatewayResilienceExecutor.executeQuery(() -> {
            attempts.incrementAndGet();
            throw new PaymentGatewayConnectionException("connection failed", null);
        })).isInstanceOf(PaymentGatewayConnectionException.class);

        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("PG request 회로 차단기는 실패율 조건을 만족하면 OPEN 으로 전이되어 fast-fail 한다")
    void requestCircuitBreakerOpensAndFastFails() {
        CircuitBreaker requestCircuitBreaker = circuitBreakerRegistry.circuitBreaker("pg-request");

        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> paymentGatewayResilienceExecutor.executeRequest(() -> {
                throw new RuntimeException("pg 5xx");
            })).isInstanceOf(RuntimeException.class);
        }

        assertThat(requestCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> paymentGatewayResilienceExecutor.executeRequest(() -> "ok"))
                .isInstanceOf(CallNotPermittedException.class);
    }
}
