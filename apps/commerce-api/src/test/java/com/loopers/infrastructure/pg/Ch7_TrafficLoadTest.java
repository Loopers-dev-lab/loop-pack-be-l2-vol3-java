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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = Ch7_TrafficLoadTest.TestConfig.class)
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
@DisplayName("Ch7. Traffic Load — 실전 트래픽")
class Ch7_TrafficLoadTest {

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
    @DisplayName("7-1. 방어책 없이 트래픽 비교 — 스레드가 많을수록 치명적")
    class NoProtection {

        @Test
        @DisplayName("10 / 50 / 100 스레드 — 방어책 없이 전부 실패, 전부 1초 대기")
        void 스레드_수별_방어책_없는_트래픽_비교() throws InterruptedException {
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .willReturn(aResponse()
                            .withStatus(500)
                            .withFixedDelay(1000)));

            int[] threadCounts = {10, 50, 100};

            System.out.println("\n=== 방어책 없이 트래픽 비교 (1초 지연 + 500 응답) ===");
            System.out.printf("%-10s %-12s %-12s%n", "스레드 수", "실패 수", "총 소요시간");

            for (int threadCount : threadCounts) {
                ExecutorService executor = Executors.newFixedThreadPool(threadCount);
                CountDownLatch latch = new CountDownLatch(threadCount);
                AtomicInteger failCount = new AtomicInteger(0);

                long start = System.currentTimeMillis();

                for (int i = 0; i < threadCount; i++) {
                    executor.submit(() -> {
                        try {
                            pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY);
                        } catch (Exception e) {
                            failCount.incrementAndGet();
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                latch.await();
                long elapsed = System.currentTimeMillis() - start;
                executor.shutdown();

                System.out.printf("%-10d %-12d %-12s%n", threadCount, failCount.get(), elapsed + "ms");

                assertThat(failCount.get()).isEqualTo(threadCount);
            }

            System.out.println("\n→ 체감: \"트래픽이 많을수록 방어책 없으면 치명적\"");
            System.out.println("  모든 스레드가 1초씩 블로킹 → 스레드 풀 고갈 위험");
        }
    }

    @Nested
    @DisplayName("7-2. Timeout만 적용 — 3초 타임아웃으로 스레드 블로킹")
    class TimeoutOnly {

        @Test
        @DisplayName("10 / 50 / 100 스레드 — Feign 타임아웃(3초)으로 블로킹, 전부 실패")
        void 타임아웃만_적용시_스레드_블로킹() throws InterruptedException {
            // 4초 지연 → Feign query readTimeout 3초 초과
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .willReturn(aResponse()
                            .withStatus(500)
                            .withFixedDelay(4000)));

            int[] threadCounts = {10, 50, 100};

            System.out.println("\n=== Timeout만 적용 (4초 지연, Feign timeout 3초) ===");
            System.out.printf("%-10s %-12s %-12s%n", "스레드 수", "실패 수", "총 소요시간");

            for (int threadCount : threadCounts) {
                ExecutorService executor = Executors.newFixedThreadPool(threadCount);
                CountDownLatch latch = new CountDownLatch(threadCount);
                AtomicInteger failCount = new AtomicInteger(0);

                long start = System.currentTimeMillis();

                for (int i = 0; i < threadCount; i++) {
                    executor.submit(() -> {
                        try {
                            pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY);
                        } catch (Exception e) {
                            failCount.incrementAndGet();
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                latch.await();
                long elapsed = System.currentTimeMillis() - start;
                executor.shutdown();

                System.out.printf("%-10d %-12d %-12s%n", threadCount, failCount.get(), elapsed + "ms");

                assertThat(failCount.get()).isEqualTo(threadCount);
            }

            System.out.println("\n→ 체감: \"timeout이 있어도 고트래픽에서는 3초 동안 스레드가 묶인다\"");
            System.out.println("  Timeout은 무한 대기를 방지하지만, 스레드 점유 자체는 막지 못한다");
        }
    }

    @Nested
    @DisplayName("7-3. Timeout + CircuitBreaker 적용 — CB가 빠르게 차단")
    class TimeoutAndCircuitBreaker {

        @Test
        @DisplayName("순차 5번 실패로 CB OPEN → 이후 동시 요청은 즉시 차단")
        void CB_적용시_빠른_차단으로_스레드_보호() throws InterruptedException {
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .willReturn(aResponse().withStatus(500)));

            int[] concurrentCounts = {10, 50, 100};

            System.out.println("\n=== Timeout + CircuitBreaker 적용 ===");
            System.out.println("Phase 1: 순차 5번 실패 → CB OPEN");
            System.out.println("Phase 2: 동시 N개 요청 → 전부 CB가 즉시 차단 (HTTP 없음)");
            System.out.printf("%-12s %-12s %-12s %-12s%n", "동시 요청 수", "CB 차단", "HTTP 호출", "총 소요시간");

            for (int concurrentCount : concurrentCounts) {
                // Phase 1: 순차적으로 5번 실패 → CB OPEN
                CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(10)
                        .failureRateThreshold(50)
                        .minimumNumberOfCalls(5)
                        .build();
                CircuitBreaker cb = CircuitBreaker.of("traffic-cb-" + concurrentCount, cbConfig);

                for (int i = 0; i < 5; i++) {
                    Supplier<PgApiResponse<PgPaymentResponse>> decorated = CircuitBreaker.decorateSupplier(cb,
                            () -> pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY));
                    try { decorated.get(); } catch (Exception ignored) {}
                }
                assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

                // Phase 2: 동시 N개 요청 → CB가 즉시 차단
                resetAllRequests();
                ExecutorService executor = Executors.newFixedThreadPool(concurrentCount);
                CountDownLatch latch = new CountDownLatch(concurrentCount);
                AtomicInteger cbBlockedCount = new AtomicInteger(0);
                AtomicInteger httpFailCount = new AtomicInteger(0);

                long start = System.currentTimeMillis();

                for (int i = 0; i < concurrentCount; i++) {
                    executor.submit(() -> {
                        try {
                            Supplier<PgApiResponse<PgPaymentResponse>> decorated = CircuitBreaker.decorateSupplier(cb,
                                    () -> pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY));
                            decorated.get();
                        } catch (CallNotPermittedException e) {
                            cbBlockedCount.incrementAndGet();
                        } catch (Exception e) {
                            httpFailCount.incrementAndGet();
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                latch.await();
                long elapsed = System.currentTimeMillis() - start;
                executor.shutdown();

                int actualHttpRequests = findAll(getRequestedFor(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))).size();

                System.out.printf("%-12d %-12d %-12d %-12s%n",
                        concurrentCount, cbBlockedCount.get(), actualHttpRequests, elapsed + "ms");

                // CB OPEN 상태이므로 모든 요청이 차단되어야 한다
                assertThat(cbBlockedCount.get()).isEqualTo(concurrentCount);
                assertThat(actualHttpRequests).isEqualTo(0);
            }

            System.out.println("\n→ 체감: \"트래픽이 많을수록 CircuitBreaker 효과가 극대화된다\"");
            System.out.println("  CB OPEN 후에는 HTTP 없이 즉시 차단 → 스레드 보호, 서버 부하 제거");
        }
    }

    @Nested
    @DisplayName("7-4. 재시도 전략별 고트래픽 부하 비교 — Thundering Herd 문제")
    class RetryStrategyUnderLoad {

        @Test
        @DisplayName("Fixed vs Randomized Jitter — 100 스레드 동시 재시도 분포 비교")
        void 재시도_전략별_시간_분포_비교() throws InterruptedException {
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .willReturn(aResponse().withStatus(500)));

            int threadCount = 100;

            // --- Strategy A: Fixed 500ms ---
            CopyOnWriteArrayList<Long> fixedTimestamps = new CopyOnWriteArrayList<>();
            long fixedBaseTime = System.currentTimeMillis();

            RetryConfig fixedConfig = RetryConfig.custom()
                    .maxAttempts(3)
                    .waitDuration(Duration.ofMillis(500))
                    .retryExceptions(FeignException.class)
                    .build();

            ExecutorService executorA = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatchA = new CountDownLatch(1);
            CountDownLatch doneLatchA = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executorA.submit(() -> {
                    try {
                        startLatchA.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    Retry retry = Retry.of("fixed-" + Thread.currentThread().getId(), fixedConfig);
                    retry.getEventPublisher().onRetry(event ->
                            fixedTimestamps.add(System.currentTimeMillis() - fixedBaseTime));

                    Supplier<PgApiResponse<PgPaymentResponse>> supplier = Retry.decorateSupplier(retry,
                            () -> pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY));
                    try { supplier.get(); } catch (Exception ignored) {}
                    doneLatchA.countDown();
                });
            }

            startLatchA.countDown();
            doneLatchA.await();
            executorA.shutdown();

            // --- Strategy B: Randomized Jitter ---
            CopyOnWriteArrayList<Long> jitterTimestamps = new CopyOnWriteArrayList<>();
            long jitterBaseTime = System.currentTimeMillis();

            RetryConfig jitterConfig = RetryConfig.custom()
                    .maxAttempts(3)
                    .intervalFunction(IntervalFunction.ofRandomized(500, 0.5))
                    .retryExceptions(FeignException.class)
                    .build();

            ExecutorService executorB = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatchB = new CountDownLatch(1);
            CountDownLatch doneLatchB = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executorB.submit(() -> {
                    try {
                        startLatchB.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    Retry retry = Retry.of("jitter-" + Thread.currentThread().getId(), jitterConfig);
                    retry.getEventPublisher().onRetry(event ->
                            jitterTimestamps.add(System.currentTimeMillis() - jitterBaseTime));

                    Supplier<PgApiResponse<PgPaymentResponse>> supplier = Retry.decorateSupplier(retry,
                            () -> pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY));
                    try { supplier.get(); } catch (Exception ignored) {}
                    doneLatchB.countDown();
                });
            }

            startLatchB.countDown();
            doneLatchB.await();
            executorB.shutdown();

            // --- 분포 분석 ---
            Map<Long, Integer> fixedBuckets = toBuckets(fixedTimestamps, 100);
            Map<Long, Integer> jitterBuckets = toBuckets(jitterTimestamps, 100);

            System.out.println("\n=== 재시도 전략별 시간 분포 (100 스레드, maxAttempts=3) ===");

            System.out.println("\n[Fixed 500ms] 재시도 시점 분포 (100ms 버킷):");
            fixedBuckets.forEach((bucket, count) ->
                    System.out.printf("  %4d~%4dms: %s (%d건)%n", bucket, bucket + 100, "█".repeat(Math.min(count, 50)), count));

            System.out.println("\n[Randomized Jitter 500ms ± 50%] 재시도 시점 분포 (100ms 버킷):");
            jitterBuckets.forEach((bucket, count) ->
                    System.out.printf("  %4d~%4dms: %s (%d건)%n", bucket, bucket + 100, "█".repeat(Math.min(count, 50)), count));

            int fixedPeakCount = fixedBuckets.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            int jitterPeakCount = jitterBuckets.values().stream().mapToInt(Integer::intValue).max().orElse(0);

            System.out.println("\n[비교]");
            System.out.println("  Fixed 최대 동시 재시도:  " + fixedPeakCount + "건");
            System.out.println("  Jitter 최대 동시 재시도: " + jitterPeakCount + "건");
            System.out.println("  Fixed 버킷 수:  " + fixedBuckets.size());
            System.out.println("  Jitter 버킷 수: " + jitterBuckets.size());

            System.out.println("\n→ 체감: \"대형 행사에서는 Randomized Jitter가 필수\"");
            System.out.println("  Fixed: 모든 스레드가 동시에 재시도 → Thundering Herd");
            System.out.println("  Jitter: 재시도가 시간적으로 분산 → 서버 부하 완화");

            // Jitter는 더 많은 버킷에 분산되어야 한다
            assertThat(jitterBuckets.size()).isGreaterThanOrEqualTo(fixedBuckets.size());
        }

        private Map<Long, Integer> toBuckets(List<Long> timestamps, int bucketSizeMs) {
            Map<Long, Integer> buckets = new TreeMap<>();
            for (long ts : timestamps) {
                long bucket = (ts / bucketSizeMs) * bucketSizeMs;
                buckets.merge(bucket, 1, Integer::sum);
            }
            return buckets;
        }
    }

    @Nested
    @DisplayName("7-5. 종합 — 서비스 규모별 최적 설정")
    class OptimalConfig {

        /**
         * WireMock 시나리오: 교대로 실패/성공 (50% 실패율)
         */
        private void stubAlternatingFailSuccess() {
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario("alternating")
                    .whenScenarioStateIs("Started")
                    .willReturn(aResponse().withStatus(500))
                    .willSetStateTo("success"));

            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario("alternating")
                    .whenScenarioStateIs("success")
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(PG_SUCCESS_RESPONSE))
                    .willSetStateTo("Started"));
        }

        @Test
        @DisplayName("인기 없는 서비스 vs 대형 행사 — 설정별 성공률/응답시간 비교")
        void 서비스_규모별_최적_설정_비교() throws InterruptedException {
            int threadCount = 100;

            // --- Config A: 인기 없는 서비스 (여유로운 설정) ---
            stubAlternatingFailSuccess();

            CircuitBreakerConfig cbConfigA = CircuitBreakerConfig.custom()
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(10)
                    .failureRateThreshold(50)
                    .minimumNumberOfCalls(10)
                    .build();
            RetryConfig retryConfigA = RetryConfig.custom()
                    .maxAttempts(3)
                    .waitDuration(Duration.ofMillis(500))
                    .retryExceptions(FeignException.class)
                    .build();

            Result resultA = runLoadTest("config-a", threadCount, cbConfigA, retryConfigA);

            // --- Config B: 대형 행사 (민감한 설정) ---
            resetAllScenarios();
            resetAllRequests();
            removeAllMappings();
            stubAlternatingFailSuccess();

            CircuitBreakerConfig cbConfigB = CircuitBreakerConfig.custom()
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(5)
                    .failureRateThreshold(40)
                    .minimumNumberOfCalls(5)
                    .build();
            RetryConfig retryConfigB = RetryConfig.custom()
                    .maxAttempts(2)
                    .waitDuration(Duration.ofMillis(200))
                    .retryExceptions(FeignException.class)
                    .build();

            Result resultB = runLoadTest("config-b", threadCount, cbConfigB, retryConfigB);

            System.out.println("\n=== 서비스 규모별 최적 설정 비교 (100 스레드, 50% 실패) ===");
            System.out.printf("%-20s %-15s %-15s%n", "", "Config A (여유)", "Config B (민감)");
            System.out.printf("%-20s %-15s %-15s%n", "설정",
                    "retry=3,wait=500ms", "retry=2,wait=200ms");
            System.out.printf("%-20s %-15s %-15s%n", "CB 설정",
                    "window=10,thr=50%", "window=5,thr=40%");
            System.out.printf("%-20s %-15d %-15d%n", "성공 수", resultA.successCount, resultB.successCount);
            System.out.printf("%-20s %-15d %-15d%n", "실패 수", resultA.failCount, resultB.failCount);
            System.out.printf("%-20s %-15s %-15s%n", "평균 응답시간",
                    resultA.avgResponseTime + "ms", resultB.avgResponseTime + "ms");
            System.out.printf("%-20s %-15s %-15s%n", "최대 응답시간",
                    resultA.maxResponseTime + "ms", resultB.maxResponseTime + "ms");
            System.out.printf("%-20s %-15s %-15s%n", "총 소요시간",
                    resultA.totalTime + "ms", resultB.totalTime + "ms");

            System.out.println("\n→ 도출: \"트래픽이 많을수록 timeout↓ retry↓ CB 민감도↑\"");
            System.out.println("  Config A: 재시도 많고 대기 길다 → 성공률↑, 응답시간↑");
            System.out.println("  Config B: 빨리 포기하고 빨리 차단 → 응답시간↓, 시스템 보호↑");

            // Config B는 더 빠르게 완료되어야 한다
            assertThat(resultB.avgResponseTime).isLessThanOrEqualTo(resultA.avgResponseTime + 100);
        }

        private Result runLoadTest(String prefix, int threadCount,
                                   CircuitBreakerConfig cbConfig, RetryConfig retryConfig) throws InterruptedException {

            CircuitBreaker cb = CircuitBreaker.of(prefix + "-cb", cbConfig);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);
            CopyOnWriteArrayList<Long> responseTimes = new CopyOnWriteArrayList<>();

            long totalStart = System.currentTimeMillis();

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    long start = System.currentTimeMillis();
                    try {
                        Retry retry = Retry.of(prefix + "-retry-" + Thread.currentThread().getId(), retryConfig);
                        Supplier<PgApiResponse<PgPaymentResponse>> decorated =
                                CircuitBreaker.decorateSupplier(cb,
                                        Retry.decorateSupplier(retry,
                                                () -> pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY)));
                        decorated.get();
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        responseTimes.add(System.currentTimeMillis() - start);
                        latch.countDown();
                    }
                });
            }

            latch.await();
            long totalTime = System.currentTimeMillis() - totalStart;
            executor.shutdown();

            long avgResponseTime = responseTimes.stream().mapToLong(Long::longValue).sum() / threadCount;
            long maxResponseTime = responseTimes.stream().mapToLong(Long::longValue).max().orElse(0);

            return new Result(successCount.get(), failCount.get(), avgResponseTime, maxResponseTime, totalTime);
        }

        record Result(int successCount, int failCount, long avgResponseTime, long maxResponseTime, long totalTime) {}
    }
}
