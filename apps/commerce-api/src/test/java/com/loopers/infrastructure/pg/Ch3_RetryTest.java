package com.loopers.infrastructure.pg;

import com.loopers.domain.payment.PaymentCommand;
import com.loopers.domain.payment.PaymentInfo;
import com.loopers.infrastructure.pg.dto.PgApiResponse;
import com.loopers.infrastructure.pg.dto.PgPaymentResponse;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = Ch3_RetryTest.TestConfig.class)
@AutoConfigureWireMock(port = 0)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "pg.base-url=http://localhost:${wiremock.server.port}",
        "spring.config.import=",
        "spring.main.allow-bean-definition-overriding=true",
        "resilience4j.circuitbreaker.configs.default.slidingWindowType=COUNT_BASED",
        "resilience4j.circuitbreaker.configs.default.slidingWindowSize=10",
        "resilience4j.circuitbreaker.configs.default.failureRateThreshold=50",
        "resilience4j.circuitbreaker.configs.default.waitDurationInOpenState=2s",
        "resilience4j.circuitbreaker.configs.default.permittedNumberOfCallsInHalfOpenState=3",
        "resilience4j.circuitbreaker.configs.default.minimumNumberOfCalls=5",
        "resilience4j.circuitbreaker.instances.pg-command.baseConfig=default",
        "resilience4j.circuitbreaker.instances.pg-query.baseConfig=default",
        "resilience4j.retry.instances.pg-query.maxAttempts=3",
        "resilience4j.retry.instances.pg-query.waitDuration=100ms"
})
@DisplayName("Ch3. Retry — 일시적 실패를 복구한다")
class Ch3_RetryTest {

    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class,
            RedisAutoConfiguration.class
    })
    @EnableFeignClients(basePackages = "com.loopers.infrastructure.pg")
    @ComponentScan(basePackages = "com.loopers.infrastructure.pg")
    static class TestConfig {}

    @Autowired
    private PgPaymentAdapter pgPaymentAdapter;

    @Autowired
    private PgQueryClient pgQueryClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private static final Long USER_ID = 1L;
    private static final String TRANSACTION_KEY = "txn-001";

    private static final PaymentCommand.PgRequest PG_REQUEST =
            new PaymentCommand.PgRequest("order-001", "CREDIT", "1234-5678-9012-3456", "10000", "http://callback.test");

    private static final String PG_SUCCESS_RESPONSE = """
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
        resetAllScenarios();
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @Nested
    @DisplayName("3-1. Retry 적용 전 vs 후")
    class RetryBeforeAfter {

        @Test
        @DisplayName("Feign 직접 호출 — retry 없이 첫 실패에서 바로 예외 발생")
        void feign_직접호출_retry_없음_첫_실패에서_예외() {
            // arrange: 첫 호출 500 → 두 번째 호출 200 (시나리오)
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario("retry-compare")
                    .whenScenarioStateIs("Started")
                    .willReturn(aResponse().withStatus(500))
                    .willSetStateTo("recovered"));

            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario("retry-compare")
                    .whenScenarioStateIs("recovered")
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(PG_SUCCESS_RESPONSE)));

            // act & assert: Feign 직접 호출 → 첫 500에서 바로 실패
            assertThatThrownBy(() -> pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY))
                    .isInstanceOf(FeignException.class);

            System.out.println("[Feign 직접 호출] 1차 시도: 500 → FeignException 발생");
            System.out.println("[Feign 직접 호출] 서버는 이미 복구됐지만, 호출자는 모른다");

            verify(1, getRequestedFor(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY)));
        }

        @Test
        @DisplayName("Adapter 호출 — retry로 일시적 실패를 자동 복구")
        void adapter_호출_retry로_일시적_실패_자동복구() {
            // arrange: 첫 호출 500 → 두 번째 호출 200
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario("retry-compare")
                    .whenScenarioStateIs("Started")
                    .willReturn(aResponse().withStatus(500))
                    .willSetStateTo("recovered"));

            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario("retry-compare")
                    .whenScenarioStateIs("recovered")
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(PG_SUCCESS_RESPONSE)));

            // act: Adapter 호출 → retry가 자동으로 재시도
            PaymentInfo result = pgPaymentAdapter.getPayment(USER_ID, TRANSACTION_KEY);

            // assert
            assertThat(result.transactionKey()).isEqualTo("txn-001");
            assertThat(result.status()).isEqualTo("APPROVED");
            verify(2, getRequestedFor(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY)));

            System.out.println("[Adapter 호출] 1차 시도: 500 → 2차 시도: 200 → 성공!");
            System.out.println("[Adapter 호출] 결과: " + result);
            System.out.println("→ 체감: \"한 번 더 시도했더니 성공했다\"");
        }
    }

    @Nested
    @DisplayName("3-2. maxAttempts별 성공률 비교")
    class MaxAttemptsComparison {

        /**
         * WireMock 시나리오: 1차 500 → 2차 500 → 3차 200 (3번째에 성공)
         * Programmatic Retry로 maxAttempts를 바꿔가며 동일 Feign Client 테스트
         */
        private void stubThirdCallSuccess() {
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario("max-attempts")
                    .whenScenarioStateIs("Started")
                    .willReturn(aResponse().withStatus(500))
                    .willSetStateTo("fail-1"));

            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario("max-attempts")
                    .whenScenarioStateIs("fail-1")
                    .willReturn(aResponse().withStatus(500))
                    .willSetStateTo("fail-2"));

            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario("max-attempts")
                    .whenScenarioStateIs("fail-2")
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(PG_SUCCESS_RESPONSE)));
        }

        private Supplier<PgApiResponse<PgPaymentResponse>> createRetrySupplier(int maxAttempts) {
            RetryConfig config = RetryConfig.custom()
                    .maxAttempts(maxAttempts)
                    .waitDuration(Duration.ofMillis(100))
                    .retryExceptions(FeignException.class)
                    .build();
            Retry retry = Retry.of("test-retry-" + maxAttempts, config);
            return Retry.decorateSupplier(retry,
                    () -> pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY));
        }

        @Test
        @DisplayName("maxAttempts=1 (재시도 없음) → 실패")
        void maxAttempts_1_재시도없음_실패() {
            stubThirdCallSuccess();

            Supplier<PgApiResponse<PgPaymentResponse>> supplier = createRetrySupplier(1);

            assertThatThrownBy(supplier::get)
                    .isInstanceOf(FeignException.class);

            System.out.println("[maxAttempts=1] 1차 시도: 500 → 실패 (재시도 없음)");
            verify(1, getRequestedFor(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY)));
        }

        @Test
        @DisplayName("maxAttempts=2 → 실패 (2번째도 500)")
        void maxAttempts_2_두번째도_500_실패() {
            stubThirdCallSuccess();

            Supplier<PgApiResponse<PgPaymentResponse>> supplier = createRetrySupplier(2);

            assertThatThrownBy(supplier::get)
                    .isInstanceOf(FeignException.class);

            System.out.println("[maxAttempts=2] 1차: 500 → 2차: 500 → 실패");
            verify(2, getRequestedFor(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY)));
        }

        @Test
        @DisplayName("maxAttempts=3 → 성공 (3번째에 200)")
        void maxAttempts_3_세번째에_성공() {
            stubThirdCallSuccess();

            Supplier<PgApiResponse<PgPaymentResponse>> supplier = createRetrySupplier(3);

            PgApiResponse<PgPaymentResponse> result = supplier.get();

            assertThat(result.data().transactionKey()).isEqualTo("txn-001");
            assertThat(result.data().status()).isEqualTo("APPROVED");
            verify(3, getRequestedFor(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY)));

            System.out.println("[maxAttempts=3] 1차: 500 → 2차: 500 → 3차: 200 → 성공!");
            System.out.println("→ 체감: \"재시도 횟수를 늘리면 성공 가능성이 올라진다\"");
        }
    }

    @Nested
    @DisplayName("3-3. 재시도 전략 비교 (Fixed vs Exponential Backoff vs Randomized Jitter)")
    class RetryStrategyComparison {

        @Test
        @DisplayName("Fixed 간격 — 일정한 대기 시간 (500ms → 500ms → 500ms)")
        void fixed_interval_일정한_대기시간() {
            // arrange: 항상 500 (타이밍 측정 목적)
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .willReturn(aResponse().withStatus(500)));

            RetryConfig config = RetryConfig.custom()
                    .maxAttempts(4)
                    .waitDuration(Duration.ofMillis(500))
                    .retryExceptions(FeignException.class)
                    .build();
            Retry retry = Retry.of("fixed-retry", config);

            List<Long> timestamps = new ArrayList<>();
            timestamps.add(System.currentTimeMillis());
            retry.getEventPublisher().onRetry(event -> timestamps.add(System.currentTimeMillis()));

            Supplier<PgApiResponse<PgPaymentResponse>> supplier = Retry.decorateSupplier(retry,
                    () -> pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY));

            // act
            assertThatThrownBy(supplier::get).isInstanceOf(FeignException.class);

            // assert & print
            System.out.println("\n=== Fixed Interval (500ms) ===");
            printIntervals(timestamps);

            assertThat(timestamps).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("Exponential Backoff — 대기 시간이 기하급수적으로 증가 (500ms → 1000ms → 2000ms)")
        void exponential_backoff_기하급수적_증가() {
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .willReturn(aResponse().withStatus(500)));

            RetryConfig config = RetryConfig.custom()
                    .maxAttempts(4)
                    .intervalFunction(IntervalFunction.ofExponentialBackoff(500, 2.0))
                    .retryExceptions(FeignException.class)
                    .build();
            Retry retry = Retry.of("exponential-retry", config);

            List<Long> timestamps = new ArrayList<>();
            timestamps.add(System.currentTimeMillis());
            retry.getEventPublisher().onRetry(event -> timestamps.add(System.currentTimeMillis()));

            Supplier<PgApiResponse<PgPaymentResponse>> supplier = Retry.decorateSupplier(retry,
                    () -> pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY));

            // act
            assertThatThrownBy(supplier::get).isInstanceOf(FeignException.class);

            // assert & print
            System.out.println("\n=== Exponential Backoff (initial=500ms, multiplier=2.0) ===");
            printIntervals(timestamps);

            assertThat(timestamps).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("Randomized Jitter — 대기 시간에 랜덤 변동 추가 (Thundering Herd 방지)")
        void randomized_jitter_랜덤_변동_추가() {
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .willReturn(aResponse().withStatus(500)));

            RetryConfig config = RetryConfig.custom()
                    .maxAttempts(4)
                    .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(500, 2.0, 0.5))
                    .retryExceptions(FeignException.class)
                    .build();
            Retry retry = Retry.of("jitter-retry", config);

            List<Long> timestamps = new ArrayList<>();
            timestamps.add(System.currentTimeMillis());
            retry.getEventPublisher().onRetry(event -> timestamps.add(System.currentTimeMillis()));

            Supplier<PgApiResponse<PgPaymentResponse>> supplier = Retry.decorateSupplier(retry,
                    () -> pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY));

            // act
            assertThatThrownBy(supplier::get).isInstanceOf(FeignException.class);

            // assert & print
            System.out.println("\n=== Randomized Jitter (initial=500ms, multiplier=2.0, randomization=0.5) ===");
            printIntervals(timestamps);
            System.out.println("→ 체감: \"Fixed: 500→500→500, Exponential: 500→1000→2000, Jitter: 변동\"");

            assertThat(timestamps).hasSizeGreaterThanOrEqualTo(2);
        }

        private void printIntervals(List<Long> timestamps) {
            for (int i = 1; i < timestamps.size(); i++) {
                long interval = timestamps.get(i) - timestamps.get(i - 1);
                System.out.printf("  시도 %d → 시도 %d: %dms%n", i, i + 1, interval);
            }
        }
    }
}
