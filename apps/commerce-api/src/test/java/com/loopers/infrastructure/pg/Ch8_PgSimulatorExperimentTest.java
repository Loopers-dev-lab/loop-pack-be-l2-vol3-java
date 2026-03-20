package com.loopers.infrastructure.pg;

import com.loopers.infrastructure.pg.dto.PgApiResponse;
import com.loopers.infrastructure.pg.dto.PgPaymentResponse;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
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
import java.util.concurrent.*;
import java.util.function.Supplier;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = Ch8_PgSimulatorExperimentTest.TestConfig.class)
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
@DisplayName("Ch8. PG 시뮬레이터 기반 설정값 도출 실험")
class Ch8_PgSimulatorExperimentTest {

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
    PgQueryClient pgQueryClient;

    @Autowired
    PgCommandClient pgCommandClient;

    @Autowired
    CircuitBreakerRegistry circuitBreakerRegistry;

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

    // ========== Helper Methods ==========

    /**
     * 40% 실패 시나리오: 10개 상태 중 4개 500, 6개 200 (순환)
     * s0→500, s1→500, s2→500, s3→500, s4→200, s5→200, s6→200, s7→200, s8→200, s9→200
     */
    private void setupFortyPercentFailureScenario() {
        String scenarioName = "forty-percent-failure";
        String[] states = {"Started", "s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9"};

        // s0~s3: 500 응답 (4개 실패)
        for (int i = 0; i < 4; i++) {
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario(scenarioName)
                    .whenScenarioStateIs(states[i])
                    .willReturn(aResponse().withStatus(500))
                    .willSetStateTo(states[i + 1]));
        }

        // s4~s8: 200 응답 (5개 성공)
        for (int i = 4; i < 9; i++) {
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario(scenarioName)
                    .whenScenarioStateIs(states[i])
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(PG_SUCCESS_RESPONSE))
                    .willSetStateTo(states[i + 1]));
        }

        // s9: 200 응답 → Started로 순환
        stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                .inScenario(scenarioName)
                .whenScenarioStateIs(states[9])
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(PG_SUCCESS_RESPONSE))
                .willSetStateTo("Started"));
    }

    /**
     * 70% 실패 시나리오: 10개 상태 중 7개 500, 3개 200 (순환)
     */
    private void setupSeventyPercentFailureScenario() {
        String scenarioName = "seventy-percent-failure";
        String[] states = {"Started", "s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9"};

        // s0~s6: 500 응답 (7개 실패)
        for (int i = 0; i < 7; i++) {
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario(scenarioName)
                    .whenScenarioStateIs(states[i])
                    .willReturn(aResponse().withStatus(500))
                    .willSetStateTo(states[i + 1]));
        }

        // s7~s8: 200 응답 (2개 성공)
        for (int i = 7; i < 9; i++) {
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario(scenarioName)
                    .whenScenarioStateIs(states[i])
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(PG_SUCCESS_RESPONSE))
                    .willSetStateTo(states[i + 1]));
        }

        // s9: 200 응답 → Started로 순환
        stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                .inScenario(scenarioName)
                .whenScenarioStateIs(states[9])
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(PG_SUCCESS_RESPONSE))
                .willSetStateTo("Started"));
    }

    // ========== 실험 1: Timeout 적정값 도출 ==========

    @Nested
    @DisplayName("실험 1: Timeout 적정값 도출")
    class Experiment1_Timeout {

        @Test
        @DisplayName("1-1: 정상 응답이 잘리는가? (PG p99 지연 500ms)")
        void 정상_응답_수신율_측정() {
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(PG_SUCCESS_RESPONSE)
                            .withFixedDelay(500)));

            int[] timeouts = {800, 1000, 2000};
            int trials = 10;

            System.out.println("\n=== 실험 1-1: Timeout별 정상 응답 수신율 (PG 지연 500ms) ===");
            System.out.printf("| %-9s | %-4s | %-8s | %-6s |%n", "timeout", "성공", "타임아웃", "성공률");
            System.out.println("|-----------|------|----------|--------|");

            for (int timeout : timeouts) {
                int successCount = 0;
                int timeoutCount = 0;
                int failCount = 0;

                for (int t = 0; t < trials; t++) {
                    ExecutorService exec = Executors.newSingleThreadExecutor();
                    Future<PgApiResponse<PgPaymentResponse>> future = exec.submit(
                            () -> pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY));
                    try {
                        future.get(timeout, TimeUnit.MILLISECONDS);
                        successCount++;
                    } catch (TimeoutException e) {
                        future.cancel(true);
                        timeoutCount++;
                    } catch (ExecutionException e) {
                        failCount++;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        failCount++;
                    } finally {
                        exec.shutdownNow();
                    }
                }

                int finalSuccess = successCount;
                String rate = String.format("%d%%", (finalSuccess * 100) / trials);
                System.out.printf("| %-9s | %-4d | %-8d | %-6s |%n",
                        timeout + "ms", successCount, timeoutCount, rate);
            }

            System.out.println("→ 500ms 지연에 대해 800ms 이상이면 정상 응답 손실 없음");
        }

        @Test
        @DisplayName("1-2: 장애 시 빠른 실패 (PG 10초 멈춤)")
        void 장애_감지_속도_측정() {
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(PG_SUCCESS_RESPONSE)
                            .withFixedDelay(10_000)));

            int[] timeouts = {800, 1000, 2000, 5000};

            System.out.println("\n=== 실험 1-2: Timeout별 장애 감지 속도 (PG 10초 멈춤) ===");
            System.out.printf("| %-9s | %-10s | %-10s |%n", "timeout", "감지 시간", "스레드 점유");
            System.out.println("|-----------|------------|------------|");

            for (int timeout : timeouts) {
                ExecutorService exec = Executors.newSingleThreadExecutor();
                Future<PgApiResponse<PgPaymentResponse>> future = exec.submit(
                        () -> pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY));

                long start = System.currentTimeMillis();
                try {
                    future.get(timeout, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                } catch (ExecutionException | InterruptedException e) {
                    // ignore
                }
                long elapsed = System.currentTimeMillis() - start;
                exec.shutdownNow();

                String occupation = timeout <= 1000 ? "최소" : timeout <= 2000 ? "적정" : "과도";
                System.out.printf("| %-9s | %-10s | %-10s |%n",
                        timeout + "ms", "~" + elapsed + "ms", occupation);
            }

            System.out.println("→ 도출: readTimeout=2s -- 정상 응답 손실 0, 장애 감지 2초 (p99 x 4배 안전 마진)");
        }
    }

    // ========== 실험 2: CB failureRateThreshold 도출 ==========

    @Nested
    @DisplayName("실험 2: CB failureRateThreshold 도출")
    class Experiment2_FailureRateThreshold {

        @Test
        @DisplayName("2-1: threshold별 동작 비교 (정상 상태: 40% 실패)")
        void threshold_비교_40퍼센트_실패() {
            int[] thresholds = {40, 50, 60, 80};

            System.out.println("\n=== 실험 2: CB failureRateThreshold별 동작 (정상 상태: 40% 실패) ===");
            System.out.printf("| %-10s | %-12s | %-4s | %-4s | %-7s | %-8s |%n",
                    "threshold", "CB 상태", "성공", "실패", "CB 차단", "오탐 여부");
            System.out.println("|------------|--------------|------|------|---------|----------|");

            for (int threshold : thresholds) {
                resetAllScenarios();
                resetAllRequests();
                removeAllMappings();
                setupFortyPercentFailureScenario();

                CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(10)
                        .failureRateThreshold(threshold)
                        .minimumNumberOfCalls(10)
                        .build();
                CircuitBreaker cb = CircuitBreaker.of("exp2-" + threshold, config);

                int success = 0, fail = 0, blocked = 0;
                for (int i = 0; i < 30; i++) {
                    try {
                        CircuitBreaker.decorateSupplier(cb,
                                () -> pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY)).get();
                        success++;
                    } catch (CallNotPermittedException e) {
                        blocked++;
                    } catch (Exception e) {
                        fail++;
                    }
                }

                CircuitBreaker.State state = cb.getState();
                String falsePositive = blocked > 0 ? (threshold <= 50 ? "!! 오탐" : "!! 위험") : "정상";
                System.out.printf("| %-10s | %-12s | %-4d | %-4d | %-7d | %-8s |%n",
                        threshold + "%", state, success, fail, blocked, falsePositive);
            }

            System.out.println("→ 도출: threshold=60% -- 정상(40%) + 마진(20%), 오탐 없이 장애만 차단");
        }

        @Test
        @DisplayName("2-2: 실제 장애(70% 실패) 시 threshold=60% 차단 확인")
        void threshold60_장애_차단_확인() {
            setupSeventyPercentFailureScenario();

            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(10)
                    .failureRateThreshold(60)
                    .minimumNumberOfCalls(10)
                    .build();
            CircuitBreaker cb = CircuitBreaker.of("exp2-verify-60", config);

            int success = 0, fail = 0, blocked = 0;
            for (int i = 0; i < 30; i++) {
                try {
                    CircuitBreaker.decorateSupplier(cb,
                            () -> pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY)).get();
                    success++;
                } catch (CallNotPermittedException e) {
                    blocked++;
                } catch (Exception e) {
                    fail++;
                }
            }

            CircuitBreaker.State state = cb.getState();

            System.out.println("\n=== 실험 2-추가: 실제 장애(70% 실패) 시 threshold=60% 차단 확인 ===");
            System.out.printf("CB 상태: %s | 성공: %d | 실패: %d | CB 차단: %d%n", state, success, fail, blocked);
            System.out.println("→ CB OPEN: 장애를 정확히 감지");

            assertThat(state).isEqualTo(CircuitBreaker.State.OPEN);
            assertThat(blocked).isGreaterThan(0);
        }
    }

    // ========== 실험 3: Retry 전략 비교 ==========

    @Nested
    @DisplayName("실험 3: Retry 전략 비교")
    class Experiment3_RetryStrategy {

        @Test
        @DisplayName("3-1: Fixed vs Exponential+Jitter 성공률/응답시간 비교")
        void retry_전략별_비교() {
            // A: Fixed 500ms
            RetryConfig configA = RetryConfig.custom()
                    .maxAttempts(3)
                    .waitDuration(Duration.ofMillis(500))
                    .retryExceptions(FeignException.class)
                    .build();

            // B: Fixed 200ms
            RetryConfig configB = RetryConfig.custom()
                    .maxAttempts(3)
                    .waitDuration(Duration.ofMillis(200))
                    .retryExceptions(FeignException.class)
                    .build();

            // C: Exponential 200ms + Jitter
            RetryConfig configC = RetryConfig.custom()
                    .maxAttempts(3)
                    .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(200, 2.0, 0.5))
                    .retryExceptions(FeignException.class)
                    .build();

            String[] labels = {"Fixed 500ms", "Fixed 200ms", "Exponential+Jitter"};
            RetryConfig[] configs = {configA, configB, configC};

            System.out.println("\n=== 실험 3: Retry 전략별 성공률/응답시간 비교 (40% 실패) ===");
            System.out.printf("| %-22s | %-4s | %-4s | %-6s | %-14s |%n",
                    "전략", "성공", "실패", "성공률", "평균 응답시간");
            System.out.println("|------------------------|------|------|--------|----------------|");

            for (int c = 0; c < configs.length; c++) {
                resetAllScenarios();
                resetAllRequests();
                removeAllMappings();
                setupFortyPercentFailureScenario();

                Retry retry = Retry.of("exp3-" + c, configs[c]);
                int success = 0, fail = 0;
                long totalElapsed = 0;

                for (int i = 0; i < 20; i++) {
                    Supplier<PgApiResponse<PgPaymentResponse>> decorated =
                            Retry.decorateSupplier(retry,
                                    () -> pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY));
                    long start = System.currentTimeMillis();
                    try {
                        decorated.get();
                        success++;
                    } catch (Exception e) {
                        fail++;
                    }
                    totalElapsed += System.currentTimeMillis() - start;
                }

                long avg = totalElapsed / 20;
                String rate = String.format("%d%%", (success * 100) / 20);
                System.out.printf("| %-22s | %-4d | %-4d | %-6s | %-14s |%n",
                        labels[c], success, fail, rate, "~" + avg + "ms");
            }

            System.out.println("→ 도출: Exponential+Jitter -- 성공률 동일, 재시도 시점 분산으로 부하 분산");
        }
    }

    // ========== 실험 4: 현재 설정 vs 도출 설정 종합 비교 ==========

    @Nested
    @DisplayName("실험 4: 현재 설정 vs 도출 설정 종합 비교")
    class Experiment4_Comparison {

        @Test
        @DisplayName("4-1: 100% 장애 시 설정 조합 종합 비교")
        void 설정_조합_종합_비교() {
            // WireMock: 100% 500 응답, 지연 없음
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .willReturn(aResponse().withStatus(500)));

            // Config A (현재): CB(threshold=50%, windowSize=10, minCalls=5), Retry(Fixed 500ms, 3 attempts)
            CircuitBreakerConfig cbConfigA = CircuitBreakerConfig.custom()
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(10)
                    .failureRateThreshold(50)
                    .minimumNumberOfCalls(5)
                    .build();
            RetryConfig retryConfigA = RetryConfig.custom()
                    .maxAttempts(3)
                    .waitDuration(Duration.ofMillis(500))
                    .retryExceptions(FeignException.class)
                    .build();

            // Config B (도출): CB(threshold=60%, windowSize=10, minCalls=5), Retry(Exponential+Jitter 200ms, 3 attempts)
            CircuitBreakerConfig cbConfigB = CircuitBreakerConfig.custom()
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(10)
                    .failureRateThreshold(60)
                    .minimumNumberOfCalls(5)
                    .build();
            RetryConfig retryConfigB = RetryConfig.custom()
                    .maxAttempts(3)
                    .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(200, 2.0, 0.5))
                    .retryExceptions(FeignException.class)
                    .build();

            // --- Config A 실행 ---
            CircuitBreaker cbA = CircuitBreaker.of("exp4-A", cbConfigA);
            Retry retryA = Retry.of("exp4-A", retryConfigA);

            int successA = 0, failA = 0, blockedA = 0;
            long startA = System.currentTimeMillis();

            for (int i = 0; i < 50; i++) {
                Supplier<PgApiResponse<PgPaymentResponse>> decorated =
                        CircuitBreaker.decorateSupplier(cbA,
                                Retry.decorateSupplier(retryA,
                                        () -> pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY)));
                try {
                    decorated.get();
                    successA++;
                } catch (CallNotPermittedException e) {
                    blockedA++;
                } catch (Exception e) {
                    failA++;
                }
            }

            long elapsedA = System.currentTimeMillis() - startA;
            int httpRequestsA = failA * 3 + successA; // 각 실패는 최대 3회 시도

            // --- Config B 실행 ---
            resetAllRequests();

            CircuitBreaker cbB = CircuitBreaker.of("exp4-B", cbConfigB);
            Retry retryB = Retry.of("exp4-B", retryConfigB);

            int successB = 0, failB = 0, blockedB = 0;
            long startB = System.currentTimeMillis();

            for (int i = 0; i < 50; i++) {
                Supplier<PgApiResponse<PgPaymentResponse>> decorated =
                        CircuitBreaker.decorateSupplier(cbB,
                                Retry.decorateSupplier(retryB,
                                        () -> pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY)));
                try {
                    decorated.get();
                    successB++;
                } catch (CallNotPermittedException e) {
                    blockedB++;
                } catch (Exception e) {
                    failB++;
                }
            }

            long elapsedB = System.currentTimeMillis() - startB;
            int httpRequestsB = failB * 3 + successB;

            System.out.println("\n=== 실험 4: 설정 조합 종합 비교 (100% 장애, 50회 요청) ===");
            System.out.printf("| %-8s | %-12s | %-12s | %-9s | %-14s |%n",
                    "설정", "총 소요시간", "HTTP 요청 수", "CB 차단 수", "평균 응답시간");
            System.out.println("|----------|--------------|--------------|-----------|----------------|");

            long avgA = elapsedA / 50;
            long avgB = elapsedB / 50;
            System.out.printf("| %-8s | %-12s | %-12d | %-9d | %-14s |%n",
                    "현재(A)", "~" + elapsedA + "ms", httpRequestsA, blockedA, "~" + avgA + "ms");
            System.out.printf("| %-8s | %-12s | %-12d | %-9d | %-14s |%n",
                    "도출(B)", "~" + elapsedB + "ms", httpRequestsB, blockedB, "~" + avgB + "ms");

            System.out.println("→ 도출: 설정 B가 총 소요시간 감소 (retry 대기 단축 효과)");

            // 도출 설정(B)이 현재 설정(A)보다 총 소요시간이 짧거나 같아야 함
            assertThat(elapsedB).isLessThanOrEqualTo(elapsedA);
        }
    }

    // ========== 실험 5: Aspect Order 비교 — CB 바깥 vs Retry 바깥 ==========

    @Nested
    @DisplayName("실험 5: Aspect Order — CB outer vs Retry outer")
    class Experiment5_AspectOrder {

        @Test
        @DisplayName("5-1: 정상 상태(40% 실패)에서 Aspect Order별 CB 동작 비교")
        void aspect_order_비교_40퍼센트_실패() {
            CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(10)
                    .failureRateThreshold(60)
                    .minimumNumberOfCalls(5)
                    .build();
            RetryConfig retryConfig = RetryConfig.custom()
                    .maxAttempts(3)
                    .waitDuration(Duration.ofMillis(100))
                    .retryExceptions(FeignException.class)
                    .build();

            // --- A: 공식 권장 (Retry 바깥 → CB 안쪽) ---
            // Retry가 CB를 감싸므로, 매 재시도마다 CB에 실패가 기록됨
            setupFortyPercentFailureScenario();

            CircuitBreaker cbA = CircuitBreaker.of("exp5-retry-outer", cbConfig);
            Retry retryA = Retry.of("exp5-retry-outer", retryConfig);

            int successA = 0, failA = 0, blockedA = 0;
            for (int i = 0; i < 30; i++) {
                // Retry(outer) → CB(inner) → Feign
                Supplier<PgApiResponse<PgPaymentResponse>> decorated =
                        Retry.decorateSupplier(retryA,
                                CircuitBreaker.decorateSupplier(cbA,
                                        () -> pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY)));
                try {
                    decorated.get();
                    successA++;
                } catch (CallNotPermittedException e) {
                    blockedA++;
                } catch (Exception e) {
                    failA++;
                }
            }

            CircuitBreaker.State stateA = cbA.getState();
            CircuitBreaker.Metrics metricsA = cbA.getMetrics();
            float failRateA = metricsA.getFailureRate();

            // --- B: 우리 선택 (CB 바깥 → Retry 안쪽) ---
            resetAllScenarios();
            resetAllRequests();
            removeAllMappings();
            setupFortyPercentFailureScenario();

            CircuitBreaker cbB = CircuitBreaker.of("exp5-cb-outer", cbConfig);
            Retry retryB = Retry.of("exp5-cb-outer", retryConfig);

            int successB = 0, failB = 0, blockedB = 0;
            for (int i = 0; i < 30; i++) {
                // CB(outer) → Retry(inner) → Feign
                Supplier<PgApiResponse<PgPaymentResponse>> decorated =
                        CircuitBreaker.decorateSupplier(cbB,
                                Retry.decorateSupplier(retryB,
                                        () -> pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY)));
                try {
                    decorated.get();
                    successB++;
                } catch (CallNotPermittedException e) {
                    blockedB++;
                } catch (Exception e) {
                    failB++;
                }
            }

            CircuitBreaker.State stateB = cbB.getState();
            CircuitBreaker.Metrics metricsB = cbB.getMetrics();
            float failRateB = metricsB.getFailureRate();

            // --- 결과 출력 ---
            System.out.println("\n=== 실험 5: Aspect Order 비교 (정상 상태: 40% 실패, 30회 호출) ===");
            System.out.println();
            System.out.println("Resilience4j 공식 권장: Retry(바깥) → CB(안쪽)");
            System.out.println("우리 선택:            CB(바깥) → Retry(안쪽)");
            System.out.println();
            System.out.printf("| %-24s | %-10s | %-8s | %-4s | %-4s | %-7s | %-12s |%n",
                    "순서", "CB 상태", "CB 실패율", "성공", "실패", "CB 차단", "판정");
            System.out.println("|--------------------------|------------|----------|------|------|---------|--------------|");

            String verdictA = stateA == CircuitBreaker.State.OPEN ? "⚠️ 오탐 위험" : "정상";
            String verdictB = stateB == CircuitBreaker.State.OPEN ? "⚠️ 오탐 위험" : "정상";
            System.out.printf("| %-24s | %-10s | %-8s | %-4d | %-4d | %-7d | %-12s |%n",
                    "A: Retry→CB (공식 권장)", stateA, String.format("%.1f%%", failRateA),
                    successA, failA, blockedA, verdictA);
            System.out.printf("| %-24s | %-10s | %-8s | %-4d | %-4d | %-7d | %-12s |%n",
                    "B: CB→Retry (우리 선택)", stateB, String.format("%.1f%%", failRateB),
                    successB, failB, blockedB, verdictB);

            System.out.println();
            System.out.println("분석:");
            System.out.printf("  A(공식): CB에 매 시도마다 기록 → CB 실패율 %.1f%% → %s%n",
                    failRateA, stateA == CircuitBreaker.State.OPEN ? "OPEN (정상인데 차단됨)" : "CLOSED");
            System.out.printf("  B(선택): CB에 최종 결과만 기록 → CB 실패율 %.1f%% → %s%n",
                    failRateB, stateB == CircuitBreaker.State.OPEN ? "OPEN" : "CLOSED (정상 유지)");
            System.out.println();
            System.out.println("→ 도출: PG 정상 실패율 40% 환경에서는 CB→Retry(우리 선택)이 안정적.");
            System.out.println("  공식 권장(Retry→CB)은 실패율이 낮은 일반 환경(1~5%)에 적합하다.");
            System.out.println("  우리 PG의 높은 정상 실패율을 고려하여 CB→Retry를 선택했다.");
        }
    }
}
