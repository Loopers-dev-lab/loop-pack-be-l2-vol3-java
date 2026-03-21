package com.loopers.infrastructure.payment.pg;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PgResult;
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
import static com.github.tomakehurst.wiremock.client.WireMock.reset;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "pg.simulator.url=http://localhost:8091",
        "spring.cloud.openfeign.client.config.pg-client.read-timeout=500",
        "resilience4j.circuitbreaker.instances.pg-request.sliding-window-size=10",
        "resilience4j.circuitbreaker.instances.pg-request.minimum-number-of-calls=10",
        "resilience4j.circuitbreaker.instances.pg-request.failure-rate-threshold=60",
        "resilience4j.circuitbreaker.instances.pg-request.slow-call-duration-threshold=10s",
        "resilience4j.circuitbreaker.instances.pg-request.slow-call-rate-threshold=100"
    }
)
@AutoConfigureWireMock(port = 8091)
@DisplayName("PgPaymentGateway Timeout + Fallback 통합 테스트")
class PgPaymentGatewayTimeoutTest {

    @Autowired
    private PgPaymentGateway pgPaymentGateway;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private static final Long ORDER_ID = 1000001L;
    private static final Long MEMBER_ID = 1L;
    private static final String TRANSACTION_KEY = "txKey-timeout-001";

    private PaymentModel paymentModel() {
        return PaymentModel.create(ORDER_ID, MEMBER_ID, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("10000"));
    }

    @BeforeEach
    void setUp() {
        reset();
        circuitBreakerRegistry.getAllCircuitBreakers()
            .forEach(io.github.resilience4j.circuitbreaker.CircuitBreaker::reset);
    }

    @Nested
    @DisplayName("readTimeout 이내 응답")
    class WithinTimeout {

        @Test
        @DisplayName("200ms 지연 + readTimeout 500ms → 정상 PgResult 반환")
        void shortDelayWithinTimeout_returnsResult() {
            stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withFixedDelay(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"transactionKey\":\"" + TRANSACTION_KEY + "\",\"status\":\"PENDING\"}")));

            PgResult result = pgPaymentGateway.requestPayment(paymentModel(), "http://localhost:8080/api/v1/payments/callback");

            assertThat(result.isAccepted()).isTrue();
            assertThat(result.pgTransactionKey()).isEqualTo(TRANSACTION_KEY);
        }
    }

    @Nested
    @DisplayName("readTimeout 초과 응답")
    class ExceedsTimeout {

        @Test
        @DisplayName("800ms 지연 + readTimeout 500ms → IOException → Fallback(UNAVAILABLE) 반환")
        void longDelayExceedsTimeout_returnsFallback() {
            stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withFixedDelay(800)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"transactionKey\":\"" + TRANSACTION_KEY + "\",\"status\":\"PENDING\"}")));

            PgResult result = pgPaymentGateway.requestPayment(paymentModel(), "http://localhost:8080/api/v1/payments/callback");

            assertThat(result.isUnavailable()).isTrue();
        }

    }
}
