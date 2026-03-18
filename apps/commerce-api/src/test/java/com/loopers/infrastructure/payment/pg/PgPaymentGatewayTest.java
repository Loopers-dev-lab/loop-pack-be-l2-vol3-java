package com.loopers.infrastructure.payment.pg;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PgResult;
import com.loopers.domain.payment.PgStatus;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.reset;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "pg.simulator.url=http://localhost:8090",
        "spring.cloud.openfeign.client.config.pg-client.read-timeout=300",
        "resilience4j.circuitbreaker.instances.pg-request.sliding-window-size=5",
        "resilience4j.circuitbreaker.instances.pg-request.minimum-number-of-calls=3",
        "resilience4j.circuitbreaker.instances.pg-request.failure-rate-threshold=60",
        "resilience4j.circuitbreaker.instances.pg-request.slow-call-duration-threshold=200ms",
        "resilience4j.circuitbreaker.instances.pg-request.slow-call-rate-threshold=60",
        "resilience4j.circuitbreaker.instances.pg-request.wait-duration-in-open-state=1s",
        "resilience4j.circuitbreaker.instances.pg-query.sliding-window-size=5",
        "resilience4j.circuitbreaker.instances.pg-query.minimum-number-of-calls=3",
        "resilience4j.circuitbreaker.instances.pg-query.failure-rate-threshold=60",
        "resilience4j.circuitbreaker.instances.pg-query.slow-call-duration-threshold=200ms",
        "resilience4j.circuitbreaker.instances.pg-query.wait-duration-in-open-state=1s",
        "resilience4j.retry.instances.pg-query.max-attempts=2",
        "resilience4j.retry.instances.pg-query.wait-duration=10ms",
        "resilience4j.retry.instances.pg-query.enable-exponential-backoff=false",
        "resilience4j.retry.instances.pg-query.enable-randomized-wait=false"
    }
)
@AutoConfigureWireMock(port = 8090)
@DisplayName("PgPaymentGateway CircuitBreaker / Retry / Bulkhead 통합 테스트")
class PgPaymentGatewayTest {

    @Autowired
    private PgPaymentGateway pgPaymentGateway;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private static final Long ORDER_ID = 1000001L;
    private static final Long MEMBER_ID = 1L;
    private static final String TRANSACTION_KEY = "txKey-gw-001";

    private PaymentModel paymentModel() {
        return PaymentModel.create(ORDER_ID, MEMBER_ID, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("10000"));
    }

    @BeforeEach
    void setUp() {
        reset();
        circuitBreakerRegistry.getAllCircuitBreakers()
            .forEach(CircuitBreaker::reset);
    }

    @Nested
    @DisplayName("CircuitBreaker 상태 전이 (pg-request)")
    class CircuitBreakerTransition {

        @Test
        @DisplayName("PG 5xx 응답이 minimumCalls 이상 쌓이면 CB가 OPEN으로 전이한다")
        void closedToOpen() {
            stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(aResponse().withStatus(500)));

            for (int i = 0; i < 3; i++) {
                pgPaymentGateway.requestPayment(paymentModel(), "http://localhost:8080/api/v1/payments/callback");
            }

            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("pg-request");
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }

        @Test
        @DisplayName("CB OPEN 상태에서 결제 요청은 fallback(unavailable)을 반환한다")
        void openReturnsFallback() {
            stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(aResponse().withStatus(500)));

            for (int i = 0; i < 3; i++) {
                pgPaymentGateway.requestPayment(paymentModel(), "http://localhost:8080/api/v1/payments/callback");
            }

            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("pg-request");
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            PgResult result = pgPaymentGateway.requestPayment(paymentModel(), "http://localhost:8080/api/v1/payments/callback");
            assertThat(result.isUnavailable()).isTrue();
        }

        @Test
        @DisplayName("CB Half-Open 상태에서 성공 응답이 쌓이면 CLOSED로 복구된다")
        void halfOpenToClosed() {
            stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"transactionKey\":\"" + TRANSACTION_KEY + "\",\"status\":\"PENDING\"}")));

            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("pg-request");
            cb.transitionToOpenState();
            cb.transitionToHalfOpenState();

            for (int i = 0; i < 3; i++) {
                pgPaymentGateway.requestPayment(paymentModel(), "http://localhost:8080/api/v1/payments/callback");
            }

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("PG 정상 응답 시 PENDING PgResult를 반환한다")
        void successReturnsResult() {
            stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"transactionKey\":\"" + TRANSACTION_KEY + "\",\"status\":\"PENDING\"}")));

            PgResult result = pgPaymentGateway.requestPayment(paymentModel(), "http://localhost:8080/api/v1/payments/callback");

            assertThat(result.isAccepted()).isTrue();
            assertThat(result.pgTransactionKey()).isEqualTo(TRANSACTION_KEY);
        }
    }

    @Nested
    @DisplayName("slowCall CB 집계 (pg-request)")
    class SlowCallThreshold {

        @Test
        @DisplayName("slowCallDurationThreshold(200ms) 초과 응답이 slowCallRateThreshold(60%) 넘으면 CB OPEN")
        void slowCallsOpenCircuitBreaker() {
            stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withFixedDelay(400)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"transactionKey\":\"" + TRANSACTION_KEY + "\",\"status\":\"PENDING\"}")));

            for (int i = 0; i < 3; i++) {
                pgPaymentGateway.requestPayment(paymentModel(), "http://localhost:8080/api/v1/payments/callback");
            }

            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("pg-request");
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }

        @Test
        @DisplayName("slowCall도 timeout(readTimeout 300ms)을 초과하면 IOException → CB failure로 집계된다")
        void verySlowCallBecomesTimeoutAndRecorded() {
            stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withFixedDelay(600)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{}")));

            for (int i = 0; i < 3; i++) {
                pgPaymentGateway.requestPayment(paymentModel(), "http://localhost:8080/api/v1/payments/callback");
            }

            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("pg-request");
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }
    }

    @Nested
    @DisplayName("CB 인스턴스 분리 검증 (pg-request vs pg-query)")
    class InstanceIsolation {

        @Test
        @DisplayName("pg-query CB가 OPEN이어도 pg-request는 독립적으로 CLOSED 유지")
        void pgQueryOpenDoesNotAffectPgRequest() {
            CircuitBreaker queryBreaker = circuitBreakerRegistry.circuitBreaker("pg-query");
            queryBreaker.transitionToOpenState();

            stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"transactionKey\":\"" + TRANSACTION_KEY + "\",\"status\":\"PENDING\"}")));

            PgResult result = pgPaymentGateway.requestPayment(paymentModel(), "http://localhost:8080/api/v1/payments/callback");

            CircuitBreaker requestBreaker = circuitBreakerRegistry.circuitBreaker("pg-request");
            assertThat(requestBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(result.isAccepted()).isTrue();
        }

        @Test
        @DisplayName("pg-request CB가 OPEN이어도 pg-query는 독립적으로 응답 가능")
        void pgRequestOpenDoesNotAffectPgQuery() throws InterruptedException {
            CircuitBreaker requestBreaker = circuitBreakerRegistry.circuitBreaker("pg-request");
            requestBreaker.transitionToOpenState();

            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {"transactionKey":"%s","orderId":"1000001","cardType":"SAMSUNG",
                         "cardNo":"1234-5678-9012-3456","amount":10000,"status":"SUCCESS","reason":"정상 승인"}
                        """.formatted(TRANSACTION_KEY))));

            PgResult result = pgPaymentGateway.getPaymentResult(TRANSACTION_KEY, MEMBER_ID);

            CircuitBreaker queryBreaker = circuitBreakerRegistry.circuitBreaker("pg-query");
            assertThat(queryBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(result.status()).isEqualTo(PgStatus.SUCCESS);
        }
    }

    @Nested
    @DisplayName("Retry 동작 검증 (pg-query)")
    class RetryBehavior {

        @Test
        @DisplayName("pg-query 조회 실패 시 maxAttempts(2)만큼 재시도 후 fallback 반환")
        void retryExhaustedReturnsFallback() {
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                .willReturn(aResponse().withStatus(500)));

            PgResult result = pgPaymentGateway.getPaymentResult(TRANSACTION_KEY, MEMBER_ID);

            assertThat(result.isUnavailable()).isTrue();
        }

        @Test
        @DisplayName("getPaymentResult 2회 호출(Retry 소진 후 CB 집계) → pg-query CB에 2회 실패 집계됨")
        void retryAttemptsCountedByCb() {
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                .willReturn(aResponse().withStatus(500)));

            pgPaymentGateway.getPaymentResult(TRANSACTION_KEY, MEMBER_ID);
            pgPaymentGateway.getPaymentResult(TRANSACTION_KEY, MEMBER_ID);

            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("pg-query");
            long failureCount = cb.getMetrics().getNumberOfFailedCalls();
            assertThat(failureCount).isGreaterThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("failureRateThreshold 검증 (pg-simulator 40% 정상 실패율)")
    class FailureRateThreshold {

        @Test
        @DisplayName("40% 실패(2/5)는 threshold(60%) 미달 → CB CLOSED 유지")
        void normalPgSimulatorFailureRateDoesNotOpenCb() {
            stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"transactionKey\":\"" + TRANSACTION_KEY + "\",\"status\":\"PENDING\"}")));

            pgPaymentGateway.requestPayment(paymentModel(), "http://localhost:8080/api/v1/payments/callback");
            pgPaymentGateway.requestPayment(paymentModel(), "http://localhost:8080/api/v1/payments/callback");
            pgPaymentGateway.requestPayment(paymentModel(), "http://localhost:8080/api/v1/payments/callback");

            reset();

            stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(aResponse().withStatus(500)));

            pgPaymentGateway.requestPayment(paymentModel(), "http://localhost:8080/api/v1/payments/callback");
            pgPaymentGateway.requestPayment(paymentModel(), "http://localhost:8080/api/v1/payments/callback");

            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("pg-request");
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

            long failures = cb.getMetrics().getNumberOfFailedCalls();
            long total = failures + cb.getMetrics().getNumberOfSuccessfulCalls();
            double failureRate = (double) failures / total * 100;
            assertThat(failureRate).isLessThan(60.0);
        }

        @Test
        @DisplayName("60% 실패(3/5)는 threshold(60%) 도달 → CB OPEN")
        void sixtyPercentFailureRateOpensCircuitBreaker() {
            stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"transactionKey\":\"" + TRANSACTION_KEY + "\",\"status\":\"PENDING\"}")));

            pgPaymentGateway.requestPayment(paymentModel(), "http://localhost:8080/api/v1/payments/callback");
            pgPaymentGateway.requestPayment(paymentModel(), "http://localhost:8080/api/v1/payments/callback");

            reset();

            stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(aResponse().withStatus(500)));

            pgPaymentGateway.requestPayment(paymentModel(), "http://localhost:8080/api/v1/payments/callback");
            pgPaymentGateway.requestPayment(paymentModel(), "http://localhost:8080/api/v1/payments/callback");
            pgPaymentGateway.requestPayment(paymentModel(), "http://localhost:8080/api/v1/payments/callback");

            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("pg-request");
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }
    }
}
