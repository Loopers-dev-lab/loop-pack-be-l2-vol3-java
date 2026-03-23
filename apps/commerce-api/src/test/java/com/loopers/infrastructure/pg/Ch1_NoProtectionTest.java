package com.loopers.infrastructure.pg;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ch1. 방어책 없는 세계
 *
 * Feign Client를 직접 호출하여 Resilience4j 보호 없이
 * 외부 API 장애가 어떤 결과를 초래하는지 체감한다.
 */
@SpringBootTest(classes = Ch1_NoProtectionTest.TestConfig.class)
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
class Ch1_NoProtectionTest {

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
    @DisplayName("1-1. 외부 API가 느려지면")
    class SlowApiTest {

        @Test
        @DisplayName("Feign 직접 호출 — 10개 스레드가 전부 ~2초간 묶인 뒤 실패한다")
        void all_threads_blocked_when_pg_is_slow() throws InterruptedException {
            // arrange: 3초 지연 (Feign command readTimeout=2s 초과)
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
            startLatch.countDown(); // 동시 출발
            doneLatch.await();
            executor.shutdown();

            // assert
            System.out.println("=== Ch1-1. 외부 API가 느려지면 (방어책 없음) ===");
            for (int i = 0; i < durations.size(); i++) {
                System.out.printf("  스레드 %2d: %,dms 대기 후 실패%n", i + 1, durations.get(i));
            }
            System.out.printf("  실패: %d/%d (전원 실패)%n", failCount.get(), threadCount);
            System.out.println("  → 보호 장치 없이 10개 스레드가 모두 ~2초간 묶였다.");
            System.out.println();

            assertThat(failCount.get()).isEqualTo(10);
            assertThat(durations).allSatisfy(d ->
                    assertThat(d).isGreaterThanOrEqualTo(1500L)
            );
        }
    }

    @Nested
    @DisplayName("1-2. 외부 API가 간헐적으로 실패하면")
    class IntermittentFailureTest {

        @Test
        @DisplayName("Feign 직접 호출 — retry 없이 첫 실패에서 바로 예외, 두 번째는 성공")
        void first_call_fails_second_succeeds_without_retry() {
            // arrange: WireMock 시나리오 — 1차 500, 2차 200
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario("intermittent")
                    .whenScenarioStateIs("Started")
                    .willReturn(aResponse().withStatus(500))
                    .willSetStateTo("recovered"));

            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .inScenario("intermittent")
                    .whenScenarioStateIs("recovered")
                    .willReturn(aResponse()
                            .withStatus(HttpStatus.OK.value())
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(PG_SUCCESS_RESPONSE)));

            System.out.println("=== Ch1-2. 외부 API가 간헐적으로 실패하면 (방어책 없음) ===");

            // act & assert: 1차 호출 — 실패
            assertThatThrownBy(() -> pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY))
                    .isInstanceOf(FeignException.class);
            System.out.println("  1차 호출: FeignException (500 응답) → 실패");

            // act & assert: 2차 호출 — 서버 복구, 성공
            var response = pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY);
            assertThat(response.data().transactionKey()).isEqualTo("txn-001");
            System.out.println("  2차 호출: 200 응답 → 성공");

            System.out.println("  → 한 번만 더 시도했으면 성공했을 텐데, retry가 없어서 첫 호출자는 실패했다.");
            System.out.println();
        }
    }

    @Nested
    @DisplayName("1-3. 외부 API가 완전히 죽으면")
    class TotalFailureTest {

        @Test
        @DisplayName("Feign 직접 호출 — 20번 전부 실패, 20번 전부 실제 HTTP 요청 발생")
        void all_requests_hit_server_even_when_always_failing() {
            // arrange: 100% 500 응답
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .willReturn(aResponse().withStatus(500)));

            int totalCalls = 20;
            int failCount = 0;

            // act: 순차 20번 호출
            for (int i = 0; i < totalCalls; i++) {
                try {
                    pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY);
                } catch (FeignException e) {
                    failCount++;
                }
            }

            // assert
            System.out.println("=== Ch1-3. 외부 API가 완전히 죽으면 (방어책 없음) ===");
            System.out.printf("  총 호출: %d회, 실패: %d회%n", totalCalls, failCount);
            System.out.println("  실제 HTTP 요청 수: " + findAll(getRequestedFor(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))).size() + "회");
            System.out.println("  → 뻔히 실패할 요청에 스레드를 낭비하고 있다. Circuit Breaker가 없으니 차단할 수 없다.");
            System.out.println();

            assertThat(failCount).isEqualTo(totalCalls);
            verify(totalCalls, getRequestedFor(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY)));
        }
    }
}
