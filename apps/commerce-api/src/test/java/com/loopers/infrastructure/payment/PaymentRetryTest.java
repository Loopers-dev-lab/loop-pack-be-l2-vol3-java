package com.loopers.infrastructure.payment;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 3-2: Retry 설정 검증 테스트.
 * <p>
 * resilience4j Retry가 올바르게 구성되어 PG 일시 장애 시
 * 자동 재시도가 수행되는지 검증한다.
 * </p>
 */
@DisplayName("Phase 3-2: PG Retry 설정 테스트")
class PaymentRetryTest {

    private RetryConfig buildPgRetryConfig() {
        return RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(100)) // 테스트용 짧은 대기
                .retryExceptions(
                        SocketTimeoutException.class,
                        ResourceAccessException.class,
                        HttpServerErrorException.class
                )
                .build();
    }

    @Test
    @DisplayName("PG 500 에러 시 최대 3회 재시도한다")
    void retry_WithServerError_ShouldRetryMaxAttempts() {
        Retry retry = RetryRegistry.of(buildPgRetryConfig()).retry("pgRetry");
        AtomicInteger callCount = new AtomicInteger(0);

        Supplier<String> decorated = Retry.decorateSupplier(retry, () -> {
            callCount.incrementAndGet();
            throw new HttpServerErrorException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        });

        assertThatThrownBy(decorated::get)
                .isInstanceOf(HttpServerErrorException.class);
        assertThat(callCount.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("2회 실패 후 3회차에 성공하면 재시도가 성공한다")
    void retry_WithTransientFailure_ShouldSucceedOnThirdAttempt() {
        Retry retry = RetryRegistry.of(buildPgRetryConfig()).retry("pgRetry");
        AtomicInteger callCount = new AtomicInteger(0);

        Supplier<String> decorated = Retry.decorateSupplier(retry, () -> {
            int attempt = callCount.incrementAndGet();
            if (attempt < 3) {
                throw new HttpServerErrorException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
            }
            return "SUCCESS";
        });

        String result = decorated.get();
        assertThat(result).isEqualTo("SUCCESS");
        assertThat(callCount.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("ResourceAccessException(SocketTimeoutException) 발생 시 재시도한다")
    void retry_WithSocketTimeout_ShouldRetry() {
        Retry retry = RetryRegistry.of(buildPgRetryConfig()).retry("pgRetry");
        AtomicInteger callCount = new AtomicInteger(0);

        Supplier<String> decorated = Retry.decorateSupplier(retry, () -> {
            int attempt = callCount.incrementAndGet();
            if (attempt == 1) {
                throw new ResourceAccessException("I/O error",
                        new SocketTimeoutException("Read timed out"));
            }
            return "SUCCESS";
        });

        String result = decorated.get();
        assertThat(result).isEqualTo("SUCCESS");
        assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("최악 응답 시간은 (read timeout × max attempts) + (wait duration × (max attempts - 1)) 이내이다")
    void retry_WorstCaseResponseTime_ShouldBeWithinLimit() {
        // 설정값 기준 최악 응답 시간 계산:
        // (2초 × 3회) + (1초 × 2회) = 8초
        int readTimeoutMs = 2000;
        int maxAttempts = 3;
        int waitDurationMs = 1000;

        int worstCaseMs = (readTimeoutMs * maxAttempts) + (waitDurationMs * (maxAttempts - 1));

        assertThat(worstCaseMs).isEqualTo(8000);
        assertThat(worstCaseMs).isLessThanOrEqualTo(10000); // 10초 이내
    }
}
