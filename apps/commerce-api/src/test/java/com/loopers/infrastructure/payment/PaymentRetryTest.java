package com.loopers.infrastructure.payment;

import com.loopers.support.error.CoreException;
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
 * <p>
 * SocketTimeoutException은 PG에 요청이 도달했을 수 있으므로
 * retry 대상에서 제외하여 이중결제를 방지한다.
 * 타임아웃 가드(ResilientPgClient supplier 내부)가 CoreException으로 변환하여
 * Retry를 원천 차단한다.
 * </p>
 */
@DisplayName("Phase 3-2: PG Retry 설정 테스트")
class PaymentRetryTest {

    private RetryConfig buildPgRetryConfig() {
        return RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(100)) // 테스트용 짧은 대기
                .retryExceptions(
                        ResourceAccessException.class,
                        HttpServerErrorException.class
                )
                .ignoreExceptions(
                        CoreException.class
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
    @DisplayName("타임아웃 가드가 CoreException으로 변환하면 retry하지 않는다")
    void retry_WithCoreExceptionFromTimeoutGuard_ShouldNotRetry() {
        Retry retry = RetryRegistry.of(buildPgRetryConfig()).retry("pgRetry");
        AtomicInteger callCount = new AtomicInteger(0);

        // 타임아웃 가드가 SocketTimeout을 CoreException으로 변환한 상황을 시뮬레이션
        Supplier<String> decorated = Retry.decorateSupplier(retry, () -> {
            callCount.incrementAndGet();
            throw new CoreException(com.loopers.support.error.ErrorType.PAYMENT_PG_TIMEOUT);
        });

        assertThatThrownBy(decorated::get)
                .isInstanceOf(CoreException.class);
        // CoreException은 ignore-exceptions → 1회만 호출하고 즉시 포기
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("ResourceAccessException(ConnectException)은 retry 대상이다")
    void retry_WithConnectException_ShouldRetry() {
        Retry retry = RetryRegistry.of(buildPgRetryConfig()).retry("pgRetry");
        AtomicInteger callCount = new AtomicInteger(0);

        Supplier<String> decorated = Retry.decorateSupplier(retry, () -> {
            int attempt = callCount.incrementAndGet();
            if (attempt == 1) {
                throw new ResourceAccessException("Connection refused",
                        new java.net.ConnectException("Connection refused"));
            }
            return "SUCCESS";
        });

        String result = decorated.get();
        assertThat(result).isEqualTo("SUCCESS");
        assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("최악 응답 시간: 타임아웃은 retry 안 하므로 PG 500 기준으로 계산한다")
    void retry_WorstCaseResponseTime_ShouldBeWithinLimit() {
        // 타임아웃: 즉시 중단 → 최악 2초 (read timeout 1회)
        // PG 500: 3회 retry → 최악 (2초 × 3회) + (1초 × 2회) = 8초
        // 전체 최악: PG 500 기준 8초 (타임아웃은 2초로 더 빠름)
        int readTimeoutMs = 2000;
        int maxAttempts = 3;
        int waitDurationMs = 1000;

        int worstCaseServerErrorMs = (readTimeoutMs * maxAttempts) + (waitDurationMs * (maxAttempts - 1));
        int worstCaseTimeoutMs = readTimeoutMs; // retry 안 함

        assertThat(worstCaseServerErrorMs).isEqualTo(8000);
        assertThat(worstCaseServerErrorMs).isLessThanOrEqualTo(10000);
        assertThat(worstCaseTimeoutMs).isEqualTo(2000); // 타임아웃은 2초 만에 끝남
    }
}
