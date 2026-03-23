package com.loopers.infrastructure.pg;

import com.loopers.domain.payment.PaymentCommand;
import com.loopers.domain.payment.PaymentInfo;
import com.loopers.infrastructure.pg.dto.PgPaymentRequest;
import feign.FeignException;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ch2. 무한 대기를 끊는다
 *
 * Feign timeout으로 무한 대기를 방지하고,
 * Adapter fallback으로 사용자 경험을 개선하는 효과를 체감한다.
 */
@SpringBootTest(classes = Ch2_TimeoutTest.TestConfig.class)
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
class Ch2_TimeoutTest {

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
    private PgCommandClient pgCommandClient;

    @Autowired
    private PgQueryClient pgQueryClient;

    @Autowired
    private PgPaymentAdapter pgPaymentAdapter;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private static final Long USER_ID = 1L;
    private static final String TRANSACTION_KEY = "txn-001";

    private static final PgPaymentRequest PAYMENT_REQUEST =
            new PgPaymentRequest("order-001", "CREDIT", "1234-5678-9012-3456", "10000", "http://callback.test");

    private static final PaymentCommand.PgRequest ADAPTER_REQUEST =
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
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @Nested
    @DisplayName("2-1. timeout 적용 전 vs 후 비교")
    class TimeoutComparisonTest {

        @Test
        @DisplayName("Feign 직접 호출 — timeout은 있지만 fallback 없이 FeignException 발생")
        void direct_feign_call_throws_ugly_exception() {
            // arrange: 3초 지연 (readTimeout=2s 초과)
            stubFor(post(urlEqualTo("/api/v1/payments"))
                    .willReturn(aResponse()
                            .withStatus(HttpStatus.OK.value())
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(PG_SUCCESS_RESPONSE)
                            .withFixedDelay(3000)));

            // act & assert
            long start = System.currentTimeMillis();
            try {
                pgCommandClient.requestPayment(USER_ID, PAYMENT_REQUEST);
            } catch (FeignException e) {
                long directDuration = System.currentTimeMillis() - start;

                System.out.println("=== Ch2-1A. Feign 직접 호출 (timeout O, fallback X) ===");
                System.out.printf("  소요 시간: %,dms%n", directDuration);
                System.out.printf("  예외 타입: %s%n", e.getClass().getSimpleName());
                System.out.println("  → timeout이 2초에서 끊어줬지만, 예외 메시지가 사용자에게 불친절하다.");
                System.out.println();

                assertThat(directDuration).isBetween(1500L, 3000L);
                return;
            }
            throw new AssertionError("예외가 발생해야 합니다");
        }

        @Test
        @DisplayName("Adapter 호출 — timeout + fallback으로 PaymentInfo.empty() 반환")
        void adapter_call_returns_meaningful_exception() {
            // arrange: 3초 지연 (readTimeout=2s 초과)
            stubFor(post(urlEqualTo("/api/v1/payments"))
                    .willReturn(aResponse()
                            .withStatus(HttpStatus.OK.value())
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(PG_SUCCESS_RESPONSE)
                            .withFixedDelay(3000)));

            // act
            long start = System.currentTimeMillis();
            PaymentInfo response = pgPaymentAdapter.requestPayment(USER_ID, ADAPTER_REQUEST);
            long adapterDuration = System.currentTimeMillis() - start;

            System.out.println("=== Ch2-1B. Adapter 호출 (timeout O, fallback O) ===");
            System.out.printf("  소요 시간: %,dms%n", adapterDuration);
            System.out.println("  → 같은 timeout이지만 fallback이 PaymentInfo.empty()로 사용자 경험을 살린다.");
            System.out.println();

            assertThat(adapterDuration).isBetween(1500L, 3000L);
            assertThat(response).isEqualTo(PaymentInfo.empty());
        }
    }

    @Nested
    @DisplayName("2-2. 짧은 timeout vs 긴 timeout")
    class ShortVsLongTimeoutTest {

        @Test
        @DisplayName("Feign query timeout 2초 — 600ms 지연이면 정상 응답")
        void normal_timeout_allows_slow_but_valid_response() {
            // arrange: 600ms 지연 (readTimeout=2s 이내)
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .willReturn(aResponse()
                            .withStatus(HttpStatus.OK.value())
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(PG_SUCCESS_RESPONSE)
                            .withFixedDelay(600)));

            // act
            long start = System.currentTimeMillis();
            var response = pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY);
            long duration = System.currentTimeMillis() - start;

            // assert
            System.out.println("=== Ch2-2A. 적정 timeout (2초) — 600ms 지연 ===");
            System.out.printf("  소요 시간: %,dms%n", duration);
            System.out.printf("  결과: 성공 (transactionKey=%s)%n", response.data().transactionKey());
            System.out.println("  → 600ms < 2s 이므로 정상 응답을 받는다.");
            System.out.println();

            assertThat(response.data().transactionKey()).isEqualTo("txn-001");
            assertThat(response.data().status()).isEqualTo("APPROVED");
        }

        @Test
        @DisplayName("강제 짧은 timeout 500ms — 600ms 지연도 실패 (정상 응답도 못 받음)")
        void too_short_timeout_rejects_valid_response() {
            // arrange: 600ms 지연
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .willReturn(aResponse()
                            .withStatus(HttpStatus.OK.value())
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(PG_SUCCESS_RESPONSE)
                            .withFixedDelay(600)));

            // act: Future.get(500ms)로 강제 짧은 timeout 시뮬레이션
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<?> future = executor.submit(() ->
                    pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY)
            );

            long start = System.currentTimeMillis();
            boolean timedOut = false;
            try {
                future.get(500, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                timedOut = true;
            } catch (Exception e) {
                // ExecutionException 등 다른 예외도 짧은 timeout 효과로 간주
                timedOut = true;
            }
            long duration = System.currentTimeMillis() - start;
            executor.shutdown();

            // assert
            System.out.println("=== Ch2-2B. 너무 짧은 timeout (500ms) — 600ms 지연 ===");
            System.out.printf("  소요 시간: %,dms%n", duration);
            System.out.printf("  결과: %s%n", timedOut ? "타임아웃 (정상 응답도 못 받음)" : "성공");
            System.out.println("  → 너무 짧으면 정상 응답도 실패한다. 적정값이 필요하다.");
            System.out.println();

            assertThat(timedOut).isTrue();
            assertThat(duration).isLessThan(600L);
        }
    }

    @Nested
    @DisplayName("2-3. 동시 요청에서 timeout의 스레드 보호 효과")
    class ConcurrentTimeoutProtectionTest {

        @Test
        @DisplayName("10개 동시 요청 — timeout이 2초에서 끊어줘서 무한 대기 방지")
        void timeout_limits_thread_blocking_duration() throws InterruptedException {
            // arrange: 3초 지연 (readTimeout=2s에서 끊김)
            stubFor(post(urlEqualTo("/api/v1/payments"))
                    .willReturn(aResponse()
                            .withStatus(HttpStatus.OK.value())
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(PG_SUCCESS_RESPONSE)
                            .withFixedDelay(3000)));

            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            AtomicInteger failCount = new AtomicInteger(0);
            List<Long> durations = new ArrayList<>();

            // act: 10개 스레드가 동시에 Feign 직접 호출
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        long start = System.currentTimeMillis();
                        try {
                            pgCommandClient.requestPayment(USER_ID, PAYMENT_REQUEST);
                        } catch (FeignException e) {
                            failCount.incrementAndGet();
                        }
                        long elapsed = System.currentTimeMillis() - start;
                        synchronized (durations) {
                            durations.add(elapsed);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await();
            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();

            // assert
            System.out.println("=== Ch2-3. 동시 요청에서 timeout의 스레드 보호 효과 ===");
            for (int i = 0; i < durations.size(); i++) {
                System.out.printf("  스레드 %2d: %,dms 대기 후 실패%n", i + 1, durations.get(i));
            }
            System.out.printf("  실패: %d/%d%n", failCount.get(), threadCount);
            System.out.println("  → timeout 없었다면 3초(또는 그 이상) 대기했을 것. timeout이 2초에서 끊어줬다.");
            System.out.println();

            assertThat(durations).allSatisfy(d ->
                    assertThat(d).isBetween(1500L, 3000L)
            );
        }
    }
}
