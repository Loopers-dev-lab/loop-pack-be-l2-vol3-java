package com.loopers.infrastructure.pg;

import com.loopers.infrastructure.pg.dto.PgApiResponse;
import com.loopers.infrastructure.pg.dto.PgPaymentResponse;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = Ch6_ConfigTuningTest.TestConfig.class)
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
@DisplayName("Ch6. Config Tuning — 어떤 값이 적절한가")
class Ch6_ConfigTuningTest {

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
    private PgQueryClient pgQueryClient;

    @Autowired
    private PgCommandClient pgCommandClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private static final Long USER_ID = 1L;
    private static final String TRANSACTION_KEY = "txn-001";

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
        removeAllMappings();
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @Nested
    @DisplayName("6-1. CircuitBreaker slidingWindowSize — 윈도우 크기에 따른 민감도")
    class SlidingWindowSize {

        /**
         * WireMock 시나리오: 처음 3번 500, 이후 200
         */
        private void stubFirst3FailThenSuccess() {
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario("window-test")
                    .whenScenarioStateIs("Started")
                    .willReturn(aResponse().withStatus(500))
                    .willSetStateTo("fail-1"));

            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario("window-test")
                    .whenScenarioStateIs("fail-1")
                    .willReturn(aResponse().withStatus(500))
                    .willSetStateTo("fail-2"));

            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario("window-test")
                    .whenScenarioStateIs("fail-2")
                    .willReturn(aResponse().withStatus(500))
                    .willSetStateTo("recovered"));

            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario("window-test")
                    .whenScenarioStateIs("recovered")
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(PG_SUCCESS_RESPONSE)));
        }

        @Test
        @DisplayName("windowSize=5 → 3/5=60% > 50% → OPEN (민감), windowSize=10 → 3/10=30% < 50% → CLOSED (둔감)")
        void 윈도우_크기별_민감도_비교() {
            // --- Config A: windowSize=5 ---
            stubFirst3FailThenSuccess();

            CircuitBreakerConfig configA = CircuitBreakerConfig.custom()
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(5)
                    .failureRateThreshold(50)
                    .minimumNumberOfCalls(3)
                    .build();
            CircuitBreaker cbA = CircuitBreaker.of("window-5", configA);

            int failCountA = 0;
            int successCountA = 0;
            for (int i = 0; i < 5; i++) {
                Supplier<PgApiResponse<PgPaymentResponse>> decorated = CircuitBreaker.decorateSupplier(cbA,
                        () -> pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY));
                try {
                    decorated.get();
                    successCountA++;
                } catch (Exception e) {
                    failCountA++;
                }
            }

            CircuitBreaker.State stateA = cbA.getState();
            System.out.println("\n=== slidingWindowSize 비교 ===");
            System.out.println("[windowSize=5] 실패: " + failCountA + "건, 성공: " + successCountA + "건 → 상태: " + stateA);

            // --- Config B: windowSize=10 ---
            resetAllScenarios();
            resetAllRequests();
            removeAllMappings();
            stubFirst3FailThenSuccess();

            CircuitBreakerConfig configB = CircuitBreakerConfig.custom()
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(10)
                    .failureRateThreshold(50)
                    .minimumNumberOfCalls(10)
                    .build();
            CircuitBreaker cbB = CircuitBreaker.of("window-10", configB);

            int failCountB = 0;
            int successCountB = 0;
            for (int i = 0; i < 10; i++) {
                Supplier<PgApiResponse<PgPaymentResponse>> decorated = CircuitBreaker.decorateSupplier(cbB,
                        () -> pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY));
                try {
                    decorated.get();
                    successCountB++;
                } catch (Exception e) {
                    failCountB++;
                }
            }

            CircuitBreaker.State stateB = cbB.getState();
            System.out.println("[windowSize=10] 실패: " + failCountB + "건, 성공: " + successCountB + "건 → 상태: " + stateB);

            System.out.println("\n→ 도출: \"윈도우가 작으면 민감하게 반응, 크면 둔감하게 반응\"");
            System.out.println("  windowSize=5: 3/5=60% > 50% → OPEN");
            System.out.println("  windowSize=10: 3/10=30% < 50% → CLOSED");

            assertThat(stateA).isEqualTo(CircuitBreaker.State.OPEN);
            assertThat(stateB).isEqualTo(CircuitBreaker.State.CLOSED);
        }
    }

    @Nested
    @DisplayName("6-2. CircuitBreaker failureRateThreshold — 임계값에 따른 차단 민감도")
    class FailureRateThreshold {

        /**
         * WireMock 시나리오: 처음 4번 500, 이후 200
         * 10번 호출 시 4/10 = 40% 실패율
         */
        private void stubFirst4FailThenSuccess() {
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario("threshold-test")
                    .whenScenarioStateIs("Started")
                    .willReturn(aResponse().withStatus(500))
                    .willSetStateTo("fail-1"));

            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario("threshold-test")
                    .whenScenarioStateIs("fail-1")
                    .willReturn(aResponse().withStatus(500))
                    .willSetStateTo("fail-2"));

            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario("threshold-test")
                    .whenScenarioStateIs("fail-2")
                    .willReturn(aResponse().withStatus(500))
                    .willSetStateTo("fail-3"));

            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario("threshold-test")
                    .whenScenarioStateIs("fail-3")
                    .willReturn(aResponse().withStatus(500))
                    .willSetStateTo("recovered"));

            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario("threshold-test")
                    .whenScenarioStateIs("recovered")
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(PG_SUCCESS_RESPONSE)));
        }

        private CircuitBreaker createCb(String name, float threshold) {
            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(10)
                    .failureRateThreshold(threshold)
                    .minimumNumberOfCalls(10)
                    .build();
            return CircuitBreaker.of(name, config);
        }

        private CircuitBreaker.State runCallsAndGetState(CircuitBreaker cb) {
            for (int i = 0; i < 10; i++) {
                Supplier<PgApiResponse<PgPaymentResponse>> decorated = CircuitBreaker.decorateSupplier(cb,
                        () -> pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY));
                try {
                    decorated.get();
                } catch (Exception ignored) {}
            }
            return cb.getState();
        }

        @Test
        @DisplayName("threshold=30% → OPEN (과민), threshold=50% → CLOSED (적정), threshold=80% → CLOSED (둔감)")
        void 임계값별_차단_민감도_비교() {
            // --- threshold=30% ---
            stubFirst4FailThenSuccess();
            CircuitBreaker cb30 = createCb("threshold-30", 30);
            CircuitBreaker.State state30 = runCallsAndGetState(cb30);

            System.out.println("\n=== failureRateThreshold 비교 (실패율 40%) ===");
            System.out.println("[threshold=30%] 40% > 30% → " + state30);

            // --- threshold=50% ---
            resetAllScenarios();
            resetAllRequests();
            removeAllMappings();
            stubFirst4FailThenSuccess();
            CircuitBreaker cb50 = createCb("threshold-50", 50);
            CircuitBreaker.State state50 = runCallsAndGetState(cb50);
            System.out.println("[threshold=50%] 40% < 50% → " + state50);

            // --- threshold=80% ---
            resetAllScenarios();
            resetAllRequests();
            removeAllMappings();
            stubFirst4FailThenSuccess();
            CircuitBreaker cb80 = createCb("threshold-80", 80);
            CircuitBreaker.State state80 = runCallsAndGetState(cb80);
            System.out.println("[threshold=80%] 40% < 80% → " + state80);

            System.out.println("\n→ 도출: \"낮으면 빨리 차단하지만 오탐(false positive) 위험\"");
            System.out.println("  30%: 정상 서버도 일시적 실패로 차단될 수 있다");
            System.out.println("  50%: 적절한 균형점");
            System.out.println("  80%: 심각한 장애만 차단, 부분 장애에는 둔감");

            assertThat(state30).isEqualTo(CircuitBreaker.State.OPEN);
            assertThat(state50).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(state80).isEqualTo(CircuitBreaker.State.CLOSED);
        }
    }

    @Nested
    @DisplayName("6-3. Retry waitDuration — 재시도 대기 시간에 따른 총 소요 시간")
    class RetryWaitDuration {

        /**
         * WireMock 시나리오: 1차 500, 2차 200
         */
        private void stubFirstFailThenSuccess() {
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario("wait-test")
                    .whenScenarioStateIs("Started")
                    .willReturn(aResponse().withStatus(500))
                    .willSetStateTo("recovered"));

            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario("wait-test")
                    .whenScenarioStateIs("recovered")
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(PG_SUCCESS_RESPONSE)));
        }

        private long measureRetryDuration(long waitDurationMs) {
            RetryConfig config = RetryConfig.custom()
                    .maxAttempts(2)
                    .waitDuration(Duration.ofMillis(waitDurationMs))
                    .retryExceptions(FeignException.class)
                    .build();
            Retry retry = Retry.of("wait-" + waitDurationMs, config);

            Supplier<PgApiResponse<PgPaymentResponse>> supplier = Retry.decorateSupplier(retry,
                    () -> pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY));

            long start = System.currentTimeMillis();
            PgApiResponse<PgPaymentResponse> result = supplier.get();
            long elapsed = System.currentTimeMillis() - start;

            assertThat(result.data().transactionKey()).isEqualTo("txn-001");
            return elapsed;
        }

        @Test
        @DisplayName("waitDuration 0ms / 500ms / 2000ms — 총 소요 시간 비교")
        void 대기시간별_총_소요시간_비교() {
            // --- waitDuration=0ms ---
            stubFirstFailThenSuccess();
            long elapsed0 = measureRetryDuration(0);

            // --- waitDuration=500ms ---
            resetAllScenarios();
            resetAllRequests();
            removeAllMappings();
            stubFirstFailThenSuccess();
            long elapsed500 = measureRetryDuration(500);

            // --- waitDuration=2000ms ---
            resetAllScenarios();
            resetAllRequests();
            removeAllMappings();
            stubFirstFailThenSuccess();
            long elapsed2000 = measureRetryDuration(2000);

            System.out.println("\n=== Retry waitDuration 비교 ===");
            System.out.println("waitDuration=0ms:    총 " + elapsed0 + "ms");
            System.out.println("waitDuration=500ms:  총 " + elapsed500 + "ms");
            System.out.println("waitDuration=2000ms: 총 " + elapsed2000 + "ms");
            System.out.println("\n→ 도출: \"길면 서버 부담↓ 복구 여유↑, 사용자 대기↑\"");
            System.out.println("  0ms: 즉시 재시도 → 서버 부하 가중, 사용자 대기 최소");
            System.out.println("  500ms: 적절한 균형");
            System.out.println("  2000ms: 서버 여유 최대, 사용자 대기 길다");

            assertThat(elapsed0).isLessThan(elapsed500);
            assertThat(elapsed500).isLessThan(elapsed2000);
        }
    }

    @Nested
    @DisplayName("6-4. Retry + Timeout 조합 — 최악의 대기 시간 계산")
    class RetryTimeoutCombo {

        @Test
        @DisplayName("maxAttempts×waitDuration 조합별 최악 소요시간 비교")
        void 조합별_최악_소요시간_비교() {
            // WireMock: 항상 500 (빠른 실패)
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .willReturn(aResponse().withStatus(500)));

            // --- Combo A: maxAttempts=3, wait=500ms ---
            // 총 대기 = (3-1) × 500ms = 1000ms
            RetryConfig configA = RetryConfig.custom()
                    .maxAttempts(3)
                    .waitDuration(Duration.ofMillis(500))
                    .retryExceptions(FeignException.class)
                    .build();
            Retry retryA = Retry.of("combo-a", configA);

            Supplier<PgApiResponse<PgPaymentResponse>> supplierA = Retry.decorateSupplier(retryA,
                    () -> pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY));

            long startA = System.currentTimeMillis();
            try { supplierA.get(); } catch (Exception ignored) {}
            long elapsedA = System.currentTimeMillis() - startA;

            // --- Combo B: maxAttempts=5, wait=100ms ---
            // 총 대기 = (5-1) × 100ms = 400ms
            resetAllRequests();
            RetryConfig configB = RetryConfig.custom()
                    .maxAttempts(5)
                    .waitDuration(Duration.ofMillis(100))
                    .retryExceptions(FeignException.class)
                    .build();
            Retry retryB = Retry.of("combo-b", configB);

            Supplier<PgApiResponse<PgPaymentResponse>> supplierB = Retry.decorateSupplier(retryB,
                    () -> pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY));

            long startB = System.currentTimeMillis();
            try { supplierB.get(); } catch (Exception ignored) {}
            long elapsedB = System.currentTimeMillis() - startB;

            System.out.println("\n=== Retry + Timeout 조합 비교 (항상 500 응답) ===");
            System.out.println("[Combo A] maxAttempts=3, wait=500ms → 총 " + elapsedA + "ms (이론값: ~1000ms)");
            System.out.println("[Combo B] maxAttempts=5, wait=100ms → 총 " + elapsedB + "ms (이론값: ~400ms)");
            System.out.println("\n→ 도출: \"총 대기시간 = (attempts-1) × waitDuration\"");
            System.out.println("  사용자 한계(예: 3초)를 넘기지 않도록 조합해야 한다");
            System.out.println("  Combo A: 재시도 적게 + 긴 대기 → 서버 부담↓, 대기↑");
            System.out.println("  Combo B: 재시도 많이 + 짧은 대기 → 성공 가능성↑, 서버 부담↑");

            // Combo A: ~1000ms, Combo B: ~400ms
            assertThat(elapsedA).isGreaterThan(800);   // 3번 시도, 2번 대기 × 500ms
            assertThat(elapsedB).isGreaterThan(300);   // 5번 시도, 4번 대기 × 100ms
            assertThat(elapsedB).isLessThan(elapsedA); // Combo B가 더 빠르다
        }
    }
}
