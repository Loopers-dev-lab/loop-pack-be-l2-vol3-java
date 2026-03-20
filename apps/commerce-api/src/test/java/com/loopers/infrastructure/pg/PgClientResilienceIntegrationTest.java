package com.loopers.infrastructure.pg;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgPaymentCommand;
import com.loopers.domain.payment.PgPaymentResult;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.github.resilience4j.retry.RetryRegistry;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PgClientResilienceIntegrationTest {

    static WireMockServer wireMockServer = new WireMockServer(
            WireMockConfiguration.wireMockConfig().dynamicPort());

    @BeforeAll
    static void startWireMock() {
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void resetState() {
        if (wireMockServer.isRunning()) {
            wireMockServer.resetAll();
        }
    }

    @DynamicPropertySource
    static void overridePgUrl(DynamicPropertyRegistry registry) {
        registry.add("pg.base-url", () -> "http://localhost:" + wireMockServer.port());
        registry.add("spring.cloud.openfeign.circuitbreaker.enabled", () -> "false");
    }

    @Autowired
    private PgClient pgClient;

    @Autowired
    private RetryRegistry retryRegistry;

    private PgPaymentCommand createCommand() {
        return new PgPaymentCommand(1L, 100L, "VISA", "4111111111111111", BigDecimal.valueOf(10000), "http://localhost/callback");
    }

    @Test
    @Order(1)
    @DisplayName("ReadTimeout 발생 시 재시도 없이 Fallback 반환")
    void readTimeout_no_retry_returns_fallback() {
        // given — 4초 지연 (read-timeout 3초 초과)
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/payments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(4000)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"transactionKey":"tx-001","orderId":"ORD-000001","status":"ACCEPTED","message":"ok"}
                                """)));

        // when
        PgPaymentResult result = pgClient.requestPayment(createCommand());

        // then — Fallback 반환 확인
        assertThat(result.accepted()).isFalse();
        assertThat(result.transactionId()).isNull();
        assertThat(result.message()).contains("PG 응답 지연");

        // then — ReadTimeout은 ConnectException이 아니므로 재시도 없이 1회만 호출
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/api/v1/payments")));
    }

    @Test
    @Order(2)
    @DisplayName("ConnectException 발생 시 Smart Retry 후 Fallback 반환")
    void connectException_triggers_retry_then_fallback() {
        // given — WireMock 서버 중지로 ConnectException 유발
        wireMockServer.stop();

        long failedBefore = retryRegistry.retry("pg-payment-request")
                .getMetrics().getNumberOfFailedCallsWithRetryAttempt();

        // when
        PgPaymentResult result = pgClient.requestPayment(createCommand());

        // then — Fallback 반환 확인
        assertThat(result.accepted()).isFalse();
        assertThat(result.transactionId()).isNull();
        assertThat(result.message()).contains("PG 응답 지연");

        // then — Retry 메트릭으로 재시도 발생 확인
        long failedAfter = retryRegistry.retry("pg-payment-request")
                .getMetrics().getNumberOfFailedCallsWithRetryAttempt();
        assertThat(failedAfter).isGreaterThan(failedBefore);
    }
}
