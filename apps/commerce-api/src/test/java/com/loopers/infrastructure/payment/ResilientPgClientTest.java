package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.GatewayPaymentResult;
import com.loopers.support.enums.CardType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ResilientPgClient 단위 테스트.
 * <p>
 * PgHttpClient를 Mock으로 주입하고, 실제 Retry/CB 인스턴스를 사용하여
 * Decorator 체인(CB outer → Retry inner → HTTP)의 동작을 검증한다.
 * </p>
 * <p>
 * 핵심 검증: Retry 소진 후 CB에 실패 1건만 기록되는지 (3건이 아님).
 * 이것이 기존 어노테이션 방식에서 발생했던 Retry 무력화 버그의 구조적 해결을 증명한다.
 * </p>
 */
@DisplayName("ResilientPgClient — Decorator 패턴 Resilience 테스트")
class ResilientPgClientTest {

    private static final Long ORDER_ID = 1L;
    private static final Long USER_ID = 1L;
    private static final CardType CARD_TYPE = CardType.SAMSUNG;
    private static final String CARD_NO = "1234-5678-9012-3456";
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(10000);
    private static final String CALLBACK_URL = "http://localhost:8080/callback";

    private PgHttpClient pgHttpClient;
    private ResilientPgClient client;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        pgHttpClient = mock(PgHttpClient.class);

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(50))
                .retryExceptions(
                        SocketTimeoutException.class,
                        ResourceAccessException.class,
                        HttpServerErrorException.class)
                .build();
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);

        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(3)
                .failureRateThreshold(60)
                .waitDurationInOpenState(Duration.ofMillis(500))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build();
        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(cbConfig);

        BulkheadRegistry bulkheadRegistry = BulkheadRegistry.ofDefaults();
        client = new ResilientPgClient(pgHttpClient, bulkheadRegistry, cbRegistry, retryRegistry);
        circuitBreaker = cbRegistry.circuitBreaker("pgCircuit");
    }

    private GatewayPaymentResult callRequestPayment() {
        return client.requestPayment(ORDER_ID, USER_ID, CARD_TYPE, CARD_NO, AMOUNT, CALLBACK_URL);
    }

    @Nested
    @DisplayName("Retry 동작 검증")
    class RetryTests {

        @Test
        @DisplayName("정상 호출 성공 시 HTTP 1회만 호출한다")
        void success_ShouldCallHttpOnce() {
            when(pgHttpClient.requestPayment(any(), any(), any(), any(), any(), any()))
                    .thenReturn(new GatewayPaymentResult("txn-1", true, "PENDING", null));

            GatewayPaymentResult result = callRequestPayment();

            assertThat(result.transactionKey()).isEqualTo("txn-1");
            verify(pgHttpClient, times(1)).requestPayment(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("일시적 실패 2회 후 3회차 성공 시 Retry가 복구한다")
        void transientFailure_ShouldRecoverOnThirdAttempt() {
            AtomicInteger count = new AtomicInteger(0);
            when(pgHttpClient.requestPayment(any(), any(), any(), any(), any(), any()))
                    .thenAnswer(inv -> {
                        if (count.incrementAndGet() < 3) {
                            throw HttpServerErrorException.create(
                                    HttpStatus.INTERNAL_SERVER_ERROR, "PG 500", null, null, null);
                        }
                        return new GatewayPaymentResult("txn-recovered", true, "PENDING", null);
                    });

            GatewayPaymentResult result = callRequestPayment();

            assertThat(result.transactionKey()).isEqualTo("txn-recovered");
            verify(pgHttpClient, times(3)).requestPayment(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Retry 3회 모두 타임아웃 시 504 PAYMENT_PG_TIMEOUT을 반환한다")
        void retryExhausted_WithTimeout_ShouldThrow504() {
            when(pgHttpClient.requestPayment(any(), any(), any(), any(), any(), any()))
                    .thenThrow(new ResourceAccessException(
                            "I/O error", new SocketTimeoutException("Read timed out")));

            assertThatThrownBy(ResilientPgClientTest.this::callRequestPayment)
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                            .isEqualTo(ErrorType.PAYMENT_PG_TIMEOUT));

            verify(pgHttpClient, times(3)).requestPayment(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Retry 3회 모두 PG 500 시 502 PAYMENT_PG_ERROR를 반환한다")
        void retryExhausted_WithServerError_ShouldThrow502() {
            when(pgHttpClient.requestPayment(any(), any(), any(), any(), any(), any()))
                    .thenThrow(HttpServerErrorException.create(
                            HttpStatus.INTERNAL_SERVER_ERROR, "PG 500", null, null, null));

            assertThatThrownBy(ResilientPgClientTest.this::callRequestPayment)
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                            .isEqualTo(ErrorType.PAYMENT_PG_ERROR));

            verify(pgHttpClient, times(3)).requestPayment(any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("예외 분류 검증")
    class ExceptionClassificationTests {

        @Test
        @DisplayName("ResourceAccessException(SocketTimeout) 래핑 시 504를 반환한다")
        void wrappedSocketTimeout_ShouldThrow504() {
            when(pgHttpClient.requestPayment(any(), any(), any(), any(), any(), any()))
                    .thenThrow(new ResourceAccessException(
                            "I/O error on POST", new SocketTimeoutException("Read timed out")));

            assertThatThrownBy(ResilientPgClientTest.this::callRequestPayment)
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                            .isEqualTo(ErrorType.PAYMENT_PG_TIMEOUT));
        }

        @Test
        @DisplayName("ResourceAccessException(non-timeout) 시 502를 반환한다")
        void resourceAccessNonTimeout_ShouldThrow502() {
            when(pgHttpClient.requestPayment(any(), any(), any(), any(), any(), any()))
                    .thenThrow(new ResourceAccessException("Connection refused"));

            assertThatThrownBy(ResilientPgClientTest.this::callRequestPayment)
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                            .isEqualTo(ErrorType.PAYMENT_PG_ERROR));
        }

        @Test
        @DisplayName("알 수 없는 예외 시 502 PAYMENT_PG_ERROR를 반환한다")
        void unknownException_ShouldThrow502() {
            when(pgHttpClient.requestPayment(any(), any(), any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("알 수 없는 오류"));

            assertThatThrownBy(ResilientPgClientTest.this::callRequestPayment)
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                            .isEqualTo(ErrorType.PAYMENT_PG_ERROR));
        }
    }

    @Nested
    @DisplayName("CircuitBreaker 동작 검증")
    class CircuitBreakerTests {

        @Test
        @DisplayName("CB OPEN 시 HTTP 호출 없이 즉시 503을 반환한다")
        void cbOpen_ShouldThrow503_WithoutHttpCall() {
            circuitBreaker.transitionToOpenState();

            assertThatThrownBy(ResilientPgClientTest.this::callRequestPayment)
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                            .isEqualTo(ErrorType.PAYMENT_SERVICE_UNAVAILABLE));

            verify(pgHttpClient, never()).requestPayment(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("CB OPEN 시 Retry 시도 없이 즉시 실패한다")
        void cbOpen_ShouldSkipRetry() {
            circuitBreaker.transitionToOpenState();

            try {
                callRequestPayment();
            } catch (CoreException ignored) {
            }

            verify(pgHttpClient, never()).requestPayment(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("연속 실패 시 CB가 OPEN 상태로 전이한다")
        void consecutiveFailures_ShouldOpenCircuitBreaker() {
            when(pgHttpClient.requestPayment(any(), any(), any(), any(), any(), any()))
                    .thenThrow(HttpServerErrorException.create(
                            HttpStatus.INTERNAL_SERVER_ERROR, "PG 500", null, null, null));

            // minimumNumberOfCalls(3) + failureRateThreshold(60%) → 3건 실패로 OPEN
            for (int i = 0; i < 3; i++) {
                try {
                    callRequestPayment();
                } catch (CoreException ignored) {
                }
            }

            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }
    }

    @Nested
    @DisplayName("Decorator 순서 검증 (핵심)")
    class DecoratorOrderTests {

        @Test
        @DisplayName("Retry 소진 후 CB에 실패 1건만 기록된다 (3건이 아님)")
        void retryExhausted_ShouldCountAsOneCbFailure() {
            when(pgHttpClient.requestPayment(any(), any(), any(), any(), any(), any()))
                    .thenThrow(HttpServerErrorException.create(
                            HttpStatus.INTERNAL_SERVER_ERROR, "PG 500", null, null, null));

            try {
                callRequestPayment();
            } catch (CoreException ignored) {
            }

            // HTTP 3회 호출 (Retry가 정상 동작)
            verify(pgHttpClient, times(3)).requestPayment(any(), any(), any(), any(), any(), any());

            // CB에는 실패 1건만 기록 (Retry 내부 시도는 CB에 보이지 않음)
            CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
            assertThat(metrics.getNumberOfFailedCalls()).isEqualTo(1);
        }

        @Test
        @DisplayName("Retry 성공 시 CB에 성공 1건이 기록된다")
        void retryRecovered_ShouldCountAsOneCbSuccess() {
            AtomicInteger count = new AtomicInteger(0);
            when(pgHttpClient.requestPayment(any(), any(), any(), any(), any(), any()))
                    .thenAnswer(inv -> {
                        if (count.incrementAndGet() < 3) {
                            throw HttpServerErrorException.create(
                                    HttpStatus.INTERNAL_SERVER_ERROR, "PG 500", null, null, null);
                        }
                        return new GatewayPaymentResult("txn-1", true, "PENDING", null);
                    });

            callRequestPayment();

            // HTTP 3회 호출 (2회 실패 + 1회 성공)
            verify(pgHttpClient, times(3)).requestPayment(any(), any(), any(), any(), any(), any());

            // CB에는 성공 1건만 기록 (Retry 내부 2회 실패는 CB에 보이지 않음)
            CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
            assertThat(metrics.getNumberOfSuccessfulCalls()).isEqualTo(1);
            assertThat(metrics.getNumberOfFailedCalls()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("위임 검증")
    class DelegationTests {

        @Test
        @DisplayName("getPaymentStatus는 PgHttpClient에 직접 위임한다")
        void getPaymentStatus_ShouldDelegateToHttpClient() {
            when(pgHttpClient.getPaymentStatus("txn-001"))
                    .thenReturn(new GatewayPaymentResult("txn-001", true, "SUCCESS", null));

            GatewayPaymentResult result = client.getPaymentStatus("txn-001");

            assertThat(result.transactionKey()).isEqualTo("txn-001");
            verify(pgHttpClient).getPaymentStatus("txn-001");
        }

        @Test
        @DisplayName("getPaymentsByOrderId는 PgHttpClient에 직접 위임한다")
        void getPaymentsByOrderId_ShouldDelegateToHttpClient() {
            when(pgHttpClient.getPaymentsByOrderId(100L)).thenReturn(List.of());

            List<GatewayPaymentResult> results = client.getPaymentsByOrderId(100L);

            assertThat(results).isEmpty();
            verify(pgHttpClient).getPaymentsByOrderId(100L);
        }
    }
}
