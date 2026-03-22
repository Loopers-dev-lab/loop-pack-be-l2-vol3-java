package com.loopers.infrastructure.payment.pg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.AopTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentRequest;
import com.loopers.support.MockPgClientConfig;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

@SpringBootTest
@Import(MockPgClientConfig.class)
class PaymentGatewayCircuitBreakerIntegrationTest {

    private PgPaymentHttpInterface pgClient;

    @Autowired
    private PgPaymentHttpInterface pgClientProxy;

    @Autowired
    private PaymentGateway paymentGateway;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        pgClient = AopTestUtils.getTargetObject(pgClientProxy);
        reset(pgClient);
        circuitBreakerRegistry.circuitBreaker("pg-payment").reset();
        circuitBreakerRegistry.circuitBreaker("pg-query").reset();
    }

    @DisplayName("결제 서킷브레이커가 동작할 때,")
    @Nested
    class PaymentCircuitBreaker {

        @DisplayName("연속 실패 시 서킷이 OPEN되고 PAYMENT_GATEWAY_UNAVAILABLE 예외가 발생한다.")
        @Test
        void throwsUnavailableWhenCircuitOpens() {
            // arrange
            given(pgClient.requestPayment(anyLong(), any(PgPaymentRequest.class)))
                    .willThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "PG 서버 오류"));

            PaymentRequest request = new PaymentRequest(
                    "order-001", "SAMSUNG", "1234-5678-9012-3456", 10000L,
                    "http://localhost:8080/api/v1/payments/callback");

            // act - 5회 실패로 서킷 OPEN 유도
            for (int i = 0; i < 5; i++) {
                try {
                    paymentGateway.requestPayment(1L, request);
                } catch (CoreException ignored) {
                }
            }

            // assert - 서킷 OPEN 상태에서 PAYMENT_GATEWAY_UNAVAILABLE 발생
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("pg-payment");
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            assertThatThrownBy(() -> paymentGateway.requestPayment(1L, request))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                            .isEqualTo(ErrorType.PAYMENT_GATEWAY_UNAVAILABLE));
        }

        @DisplayName("PG 4xx 응답 5회 발생해도 서킷은 CLOSED 상태를 유지한다.")
        @Test
        void circuitStaysClosedOn4xxErrors() {
            // arrange
            given(pgClient.requestPayment(anyLong(), any(PgPaymentRequest.class)))
                    .willThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "잘못된 요청"));

            PaymentRequest request = new PaymentRequest(
                    "order-001", "SAMSUNG", "1234-5678-9012-3456", 10000L,
                    "http://localhost:8080/api/v1/payments/callback");

            // act - 5회 호출 (4xx는 ignore-exceptions이므로 실패로 기록되지 않음)
            for (int i = 0; i < 5; i++) {
                try {
                    paymentGateway.requestPayment(1L, request);
                } catch (CoreException ignored) {
                }
            }

            // assert - 서킷은 CLOSED 유지
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("pg-payment");
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @DisplayName("PG 5xx 응답 5회 발생 시 서킷이 OPEN된다.")
        @Test
        void circuitOpensOn5xxErrors() {
            // arrange
            given(pgClient.requestPayment(anyLong(), any(PgPaymentRequest.class)))
                    .willThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "PG 서버 오류"));

            PaymentRequest request = new PaymentRequest(
                    "order-001", "SAMSUNG", "1234-5678-9012-3456", 10000L,
                    "http://localhost:8080/api/v1/payments/callback");

            // act - 5회 실패로 서킷 OPEN 유도
            for (int i = 0; i < 5; i++) {
                try {
                    paymentGateway.requestPayment(1L, request);
                } catch (CoreException ignored) {
                }
            }

            // assert - 서킷 OPEN
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("pg-payment");
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }
    }

    @DisplayName("조회 서킷브레이커가 동작할 때,")
    @Nested
    class QueryCircuitBreaker {

        @DisplayName("연속 실패 시 서킷이 OPEN되고 PAYMENT_GATEWAY_UNAVAILABLE 예외가 발생한다.")
        @Test
        void throwsUnavailableWhenQueryCircuitOpens() {
            // arrange
            given(pgClient.getTransaction(anyLong(), anyString()))
                    .willThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "PG 서버 오류"));

            // act - 5회 실패로 서킷 OPEN 유도
            for (int i = 0; i < 5; i++) {
                try {
                    paymentGateway.getTransaction(1L, "tx-key-001");
                } catch (CoreException ignored) {
                }
            }

            // assert
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("pg-query");
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            assertThatThrownBy(() -> paymentGateway.getTransaction(1L, "tx-key-001"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                            .isEqualTo(ErrorType.PAYMENT_GATEWAY_UNAVAILABLE));
        }

        @DisplayName("getTransaction 실패가 getTransactionsByOrder에도 서킷 OPEN을 유발한다.")
        @Test
        void sharedQueryCircuitAffectsBothMethods() {
            // arrange
            given(pgClient.getTransaction(anyLong(), anyString()))
                    .willThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "PG 서버 오류"));

            // act - getTransaction 5회 실패로 pg-query 서킷 OPEN 유도
            for (int i = 0; i < 5; i++) {
                try {
                    paymentGateway.getTransaction(1L, "tx-key-001");
                } catch (CoreException ignored) {
                }
            }

            // assert - getTransactionsByOrder도 서킷 OPEN으로 차단
            assertThatThrownBy(() -> paymentGateway.getTransactionsByOrder(1L, "order-001"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                            .isEqualTo(ErrorType.PAYMENT_GATEWAY_UNAVAILABLE));
        }
    }

    @DisplayName("서킷 분리가 동작할 때,")
    @Nested
    class CircuitIsolation {

        @DisplayName("쓰기 서킷 OPEN이 읽기 서킷에 영향을 주지 않는다.")
        @Test
        void paymentCircuitDoesNotAffectQueryCircuit() {
            // arrange - 결제 서킷 OPEN 유도
            given(pgClient.requestPayment(anyLong(), any(PgPaymentRequest.class)))
                    .willThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "PG 서버 오류"));

            PgTransactionDetailResponse detailResponse = new PgTransactionDetailResponse(
                    "tx-key-001", "order-001", "SAMSUNG", "1234-5678-9012-3456",
                    10000L, "SUCCESS", "정상 승인되었습니다.");
            given(pgClient.getTransaction(anyLong(), anyString()))
                    .willReturn(new PgApiResponse<>(
                            new PgApiResponse.Metadata("SUCCESS", null, null), detailResponse));

            PaymentRequest request = new PaymentRequest(
                    "order-001", "SAMSUNG", "1234-5678-9012-3456", 10000L,
                    "http://localhost:8080/api/v1/payments/callback");

            for (int i = 0; i < 5; i++) {
                try {
                    paymentGateway.requestPayment(1L, request);
                } catch (CoreException ignored) {
                }
            }

            // act & assert - 결제 서킷은 OPEN, 조회 서킷은 CLOSED
            CircuitBreaker paymentCircuit = circuitBreakerRegistry.circuitBreaker("pg-payment");
            CircuitBreaker queryCircuit = circuitBreakerRegistry.circuitBreaker("pg-query");

            assertThat(paymentCircuit.getState()).isEqualTo(CircuitBreaker.State.OPEN);
            assertThat(queryCircuit.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

            // 조회는 정상 동작
            assertThat(paymentGateway.getTransaction(1L, "tx-key-001").transactionKey())
                    .isEqualTo("tx-key-001");
        }
    }
}
