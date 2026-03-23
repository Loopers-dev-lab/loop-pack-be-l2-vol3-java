package com.loopers.infrastructure.pg;

import com.loopers.domain.payment.PaymentCommand;
import com.loopers.domain.payment.PaymentInfo;
import com.loopers.infrastructure.pg.dto.PgPaymentRequest;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PgPaymentAdapterResilienceTest.TestConfig.class)
@AutoConfigureWireMock(port = 0)
@ActiveProfiles({"test", "resilience-test"})
@TestPropertySource(properties = {
        "pg.base-url=http://localhost:${wiremock.server.port}",
        "spring.config.import=",
        "spring.main.allow-bean-definition-overriding=true"
})
class PgPaymentAdapterResilienceTest {

    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class,
            RedisAutoConfiguration.class
    })
    @EnableFeignClients(basePackages = "com.loopers.infrastructure.pg")
    @ComponentScan(
            basePackages = "com.loopers.infrastructure.pg",
            excludeFilters = @ComponentScan.Filter(
                    type = FilterType.REGEX,
                    pattern = "com\\.loopers\\.(?!infrastructure\\.pg|support).*"
            )
    )
    static class TestConfig {
    }

    @Autowired
    private PgPaymentAdapter pgPaymentAdapter;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private static final Long USER_ID = 1L;
    private static final String TRANSACTION_KEY = "txn-001";
    private static final String ORDER_ID = "order-001";
    private static final PaymentCommand.PgRequest PAYMENT_REQUEST =
            new PaymentCommand.PgRequest("order-001", "CREDIT", "1234-5678-9012-3456", "10000", "http://callback.test");

    private static final String SUCCESS_RESPONSE_BODY = """
            {
                "meta": {"result": "SUCCESS", "errorCode": null, "message": null},
                "data": {
                    "transactionKey": "txn-001",
                    "orderId": "order-001",
                    "cardType": "CREDIT",
                    "cardNo": "1234-5678-9012-3456",
                    "amount": "10000",
                    "status": "APPROVED"
                }
            }
            """;

    @BeforeEach
    void setUp() {
        resetAllRequests();
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @Nested
    @DisplayName("섹션 1: Feign Timeout 테스트")
    class FeignTimeoutTest {

        @Test
        @DisplayName("원인: PG 서버 응답이 pg-command readTimeout(2s)보다 느림 → 결과: 타임아웃 발생 후 fallback으로 PaymentInfo.empty() 반환")
        void pgCommand_timeout_triggers_fallback() {
            // arrange: 3초 지연 (readTimeout 2s 초과)
            stubFor(post(urlEqualTo("/api/v1/payments"))
                    .willReturn(aResponse()
                            .withStatus(HttpStatus.OK.value())
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(SUCCESS_RESPONSE_BODY)
                            .withFixedDelay(3000)));

            // act
            PaymentInfo response = pgPaymentAdapter.requestPayment(USER_ID, PAYMENT_REQUEST);

            // assert: fallback으로 empty() 반환
            assertThat(response).isNotNull();
            assertThat(response.transactionKey()).isNull();
            assertThat(response.orderId()).isNull();

            verify(1, postRequestedFor(urlEqualTo("/api/v1/payments")));
        }

        @Test
        @DisplayName("원인: PG 서버 응답이 pg-query readTimeout(2s)보다 느림 → 결과: Retry 3회 모두 타임아웃 후 fallback으로 empty() 반환")
        void pgQuery_timeout_retries_then_fallback() {
            // arrange: 3초 지연 (readTimeout 2s 초과)
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .willReturn(aResponse()
                            .withStatus(HttpStatus.OK.value())
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(SUCCESS_RESPONSE_BODY)
                            .withFixedDelay(3000)));

            // act
            PaymentInfo response = pgPaymentAdapter.getPayment(USER_ID, TRANSACTION_KEY);

            // assert: fallback으로 empty() 반환
            assertThat(response).isNotNull();
            assertThat(response.transactionKey()).isNull();
            assertThat(response.orderId()).isNull();

            // 3회 재시도 확인
            verify(3, getRequestedFor(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY)));
        }
    }

    @Nested
    @DisplayName("섹션 2: Retry 테스트")
    class RetryTest {

        @Test
        @DisplayName("원인: pg-query 첫 2회 500 응답, 3회째 200 → 결과: 최종 성공 (요청 3회)")
        void pgQuery_retries_until_success() {
            // arrange: 시나리오 기반 - 첫 2회 실패, 3회째 성공
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario("retry-success")
                    .whenScenarioStateIs("Started")
                    .willReturn(aResponse().withStatus(500))
                    .willSetStateTo("first-fail"));

            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario("retry-success")
                    .whenScenarioStateIs("first-fail")
                    .willReturn(aResponse().withStatus(500))
                    .willSetStateTo("second-fail"));

            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario("retry-success")
                    .whenScenarioStateIs("second-fail")
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(SUCCESS_RESPONSE_BODY)));

            // act
            PaymentInfo response = pgPaymentAdapter.getPayment(USER_ID, TRANSACTION_KEY);

            // assert
            assertThat(response.transactionKey()).isEqualTo("txn-001");
            assertThat(response.status()).isEqualTo("APPROVED");
            verify(3, getRequestedFor(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY)));
        }

        @Test
        @DisplayName("원인: pg-query 3회 모두 500 응답 → 결과: fallback(empty()) 반환, 요청 3회 확인")
        void pgQuery_all_retries_fail_returns_fallback() {
            // arrange
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .willReturn(aResponse().withStatus(500)));

            // act
            PaymentInfo response = pgPaymentAdapter.getPayment(USER_ID, TRANSACTION_KEY);

            // assert
            assertThat(response.transactionKey()).isNull();
            assertThat(response.orderId()).isNull();
            verify(3, getRequestedFor(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY)));
        }

        @Test
        @DisplayName("원인: pg-command 500 응답 → 결과: 재시도 없이 바로 fallback(PaymentInfo.empty()), 요청 1회만")
        void pgCommand_no_retry_on_failure() {
            // arrange
            stubFor(post(urlEqualTo("/api/v1/payments"))
                    .willReturn(aResponse().withStatus(500)));

            // act
            PaymentInfo response = pgPaymentAdapter.requestPayment(USER_ID, PAYMENT_REQUEST);

            // assert: fallback으로 empty() 반환, 재시도 없이 1회만
            assertThat(response).isEqualTo(PaymentInfo.empty());
            verify(1, postRequestedFor(urlEqualTo("/api/v1/payments")));
        }
    }

    @Nested
    @DisplayName("섹션 3: Fallback 테스트")
    class FallbackTest {

        @Test
        @DisplayName("원인: pg-command 500 응답 → 결과: fallback으로 PaymentInfo.empty() 반환")
        void pgCommand_fallback_returns_empty() {
            // arrange
            stubFor(post(urlEqualTo("/api/v1/payments"))
                    .willReturn(aResponse().withStatus(500)));

            // act
            PaymentInfo response = pgPaymentAdapter.requestPayment(USER_ID, PAYMENT_REQUEST);

            // assert
            assertThat(response).isEqualTo(PaymentInfo.empty());
        }

        @Test
        @DisplayName("원인: pg-query 500 응답 + 3회 retry 소진 → 결과: PgPaymentResponse.empty() 반환")
        void pgQuery_fallback_returns_empty() {
            // arrange
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .willReturn(aResponse().withStatus(500)));

            // act
            PaymentInfo response = pgPaymentAdapter.getPayment(USER_ID, TRANSACTION_KEY);

            // assert
            assertThat(response).isEqualTo(PaymentInfo.empty());
        }

        @Test
        @DisplayName("원인: fallback으로 반환된 empty() → 결과: 모든 필드가 null")
        void empty_response_has_all_null_fields() {
            // arrange
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .willReturn(aResponse().withStatus(500)));

            // act
            PaymentInfo response = pgPaymentAdapter.getPayment(USER_ID, TRANSACTION_KEY);

            // assert
            assertThat(response.transactionKey()).isNull();
            assertThat(response.orderId()).isNull();
            assertThat(response.cardType()).isNull();
            assertThat(response.cardNo()).isNull();
            assertThat(response.amount()).isNull();
            assertThat(response.status()).isNull();
        }
    }

    @Nested
    @DisplayName("섹션 4: CircuitBreaker 상태 전이 테스트")
    class CircuitBreakerStateTransitionTest {

        @Test
        @DisplayName("원인: 실패율 50% 초과 (minimumNumberOfCalls=3 이상) → 결과: CLOSED에서 OPEN으로 전이")
        void closedToOpen_when_failure_rate_exceeds_threshold() {
            // arrange
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("pg-command");
            stubFor(post(urlEqualTo("/api/v1/payments"))
                    .willReturn(aResponse().withStatus(500)));

            // act: minimumNumberOfCalls(3) 이상 호출하여 실패율 100% 달성
            for (int i = 0; i < 3; i++) {
                pgPaymentAdapter.requestPayment(USER_ID, PAYMENT_REQUEST);
            }

            // assert: OPEN 상태로 전이
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }

        @Test
        @DisplayName("원인: CircuitBreaker OPEN 상태 → 결과: HTTP 요청 없이 바로 fallback 반환")
        void open_state_returns_fallback_without_http_call() {
            // arrange: OPEN 상태로 전이시킴
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("pg-command");
            stubFor(post(urlEqualTo("/api/v1/payments"))
                    .willReturn(aResponse().withStatus(500)));

            for (int i = 0; i < 3; i++) {
                pgPaymentAdapter.requestPayment(USER_ID, PAYMENT_REQUEST);
            }
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            // WireMock 요청 카운트 리셋
            resetAllRequests();

            // 성공 응답으로 변경 (하지만 OPEN이므로 요청이 안 가야 함)
            resetAllScenarios();
            stubFor(post(urlEqualTo("/api/v1/payments"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(SUCCESS_RESPONSE_BODY)));

            // act: OPEN 상태에서 호출 → fallback으로 empty() 반환
            PaymentInfo blockedResponse = pgPaymentAdapter.requestPayment(USER_ID, PAYMENT_REQUEST);
            assertThat(blockedResponse).isEqualTo(PaymentInfo.empty());

            // assert: HTTP 요청이 나가지 않음
            verify(0, postRequestedFor(urlEqualTo("/api/v1/payments")));
        }

        @Test
        @DisplayName("원인: OPEN → waitDuration(2s) 대기 → HALF_OPEN에서 성공 → 결과: CLOSED로 복구")
        void halfOpen_to_closed_on_success() throws InterruptedException {
            // arrange: OPEN 상태로 전이
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("pg-query");
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .willReturn(aResponse().withStatus(500)));

            // minimumNumberOfCalls(3) 호출 - pg-query는 retry 3회씩이므로 3번 호출 = 9 requests
            for (int i = 0; i < 3; i++) {
                pgPaymentAdapter.getPayment(USER_ID, TRANSACTION_KEY);
            }
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            // waitDurationInOpenState(2s) 대기
            Thread.sleep(2500);

            // HALF_OPEN 상태 확인 - 성공 응답으로 stub 교체
            resetAllRequests();
            removeAllMappings();
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(SUCCESS_RESPONSE_BODY)));

            // act: permittedNumberOfCallsInHalfOpenState(2)만큼 성공 호출
            for (int i = 0; i < 2; i++) {
                PaymentInfo response = pgPaymentAdapter.getPayment(USER_ID, TRANSACTION_KEY);
                assertThat(response.transactionKey()).isEqualTo("txn-001");
            }

            // assert: CLOSED로 복구
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }
    }
}
