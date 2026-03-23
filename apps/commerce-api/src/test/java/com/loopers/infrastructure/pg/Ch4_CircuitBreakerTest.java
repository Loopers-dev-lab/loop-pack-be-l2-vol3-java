package com.loopers.infrastructure.pg;

import com.loopers.domain.payment.PaymentCommand;
import com.loopers.domain.payment.PaymentInfo;
import com.loopers.infrastructure.pg.dto.PgApiResponse;
import com.loopers.infrastructure.pg.dto.PgPaymentRequest;
import com.loopers.infrastructure.pg.dto.PgPaymentResponse;
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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = Ch4_CircuitBreakerTest.TestConfig.class)
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
@DisplayName("Ch4. CircuitBreaker — 죽은 서버를 차단한다")
class Ch4_CircuitBreakerTest {

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
    private PgCommandClient pgCommandClient;

    @Autowired
    private PgQueryClient pgQueryClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private static final Long USER_ID = 1L;
    private static final String TRANSACTION_KEY = "txn-001";

    private static final PaymentCommand.PgRequest PG_REQUEST =
            new PaymentCommand.PgRequest("order-001", "CREDIT", "1234-5678-9012-3456", "10000", "http://callback.test");

    private static final PgPaymentRequest FEIGN_REQUEST =
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
        resetAllScenarios();
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @Nested
    @DisplayName("4-1. CircuitBreaker 적용 전 vs 후")
    class CircuitBreakerBeforeAfter {

        @Test
        @DisplayName("Feign 직접 호출 20번 — CB 없이 20번 전부 서버에 도달, 20번 전부 실패")
        void feign_직접호출_20번_전부_서버에_도달() {
            // arrange
            stubFor(post(urlEqualTo("/api/v1/payments"))
                    .willReturn(aResponse().withStatus(500)));

            // act: 20번 직접 호출
            int failCount = 0;
            for (int i = 0; i < 20; i++) {
                try {
                    pgCommandClient.requestPayment(USER_ID, FEIGN_REQUEST);
                } catch (FeignException e) {
                    failCount++;
                }
            }

            // assert: 20번 전부 서버에 도달, 20번 전부 실패
            verify(20, postRequestedFor(urlEqualTo("/api/v1/payments")));
            assertThat(failCount).isEqualTo(20);

            System.out.println("[Feign 직접 호출] 20번 전부 서버에 도달 → 20번 전부 실패");
            System.out.println("[Feign 직접 호출] 서버가 죽었는데도 계속 요청을 보냈다");
        }

        @Test
        @DisplayName("Adapter 호출 20번 — CB가 5번 실패 후 나머지 15번 차단")
        void adapter_호출_20번_CB가_15번_차단() {
            // arrange
            stubFor(post(urlEqualTo("/api/v1/payments"))
                    .willReturn(aResponse().withStatus(500)));

            // act: 20번 Adapter 호출 (fallback으로 PaymentInfo.empty() 반환, 예외 없음)
            int fallbackCount = 0;
            for (int i = 0; i < 20; i++) {
                PaymentInfo result = pgPaymentAdapter.requestPayment(USER_ID, PG_REQUEST);
                if (result.equals(PaymentInfo.empty())) {
                    fallbackCount++;
                }
            }

            // assert: minimumNumberOfCalls=5 → 5번 실패 후 OPEN → 나머지 15번은 서버에 안 감
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("pg-command");
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
            assertThat(fallbackCount).isEqualTo(20); // 전부 empty() 반환 (직접 호출 5 + CB fallback 15)

            int actualRequests = findAll(postRequestedFor(urlEqualTo("/api/v1/payments"))).size();
            int blockedRequests = 20 - actualRequests;

            System.out.println("[Adapter 호출] 실제 서버 요청: " + actualRequests + "번");
            System.out.println("[Adapter 호출] CB가 차단한 요청: " + blockedRequests + "번");
            System.out.println("→ 체감: \"" + blockedRequests + "번의 헛된 요청을 막았다\"");

            assertThat(actualRequests).isEqualTo(5);
            assertThat(blockedRequests).isEqualTo(15);
        }
    }

    @Nested
    @DisplayName("4-2. 상태 전이 관찰 (CLOSED → OPEN → HALF_OPEN → CLOSED)")
    class StateTransition {

        @Test
        @DisplayName("CLOSED → OPEN → HALF_OPEN → CLOSED 전체 생명주기")
        void 전체_상태_전이_생명주기() throws InterruptedException {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("pg-query");

            // Step 1: CLOSED 상태 확인
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            System.out.println("[Step 1] 초기 상태: " + cb.getState());

            // Step 2: 연속 실패 → OPEN
            // CB → Retry → Feign: 1 adapter call = 1 CB count (retry 3회 후 최종 실패)
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .willReturn(aResponse().withStatus(500)));

            for (int i = 0; i < 5; i++) {
                pgPaymentAdapter.getPayment(USER_ID, TRANSACTION_KEY);
            }
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
            System.out.println("[Step 2] 5번 실패 후: " + cb.getState());

            // Step 3: OPEN 상태에서 요청 차단 확인
            resetAllRequests();
            PaymentInfo blockedResult = pgPaymentAdapter.getPayment(USER_ID, TRANSACTION_KEY);
            assertThat(blockedResult).isEqualTo(PaymentInfo.empty());
            verify(0, getRequestedFor(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY)));
            System.out.println("[Step 3] OPEN 상태에서 호출 → HTTP 요청 0건, fallback 반환: " + blockedResult);

            // Step 4: waitDuration(2s) 대기 → HALF_OPEN
            Thread.sleep(2500);

            // Step 5: HALF_OPEN에서 성공 → CLOSED
            removeAllMappings();
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(PG_SUCCESS_RESPONSE)));

            resetAllRequests();
            for (int i = 0; i < 3; i++) {
                PaymentInfo result = pgPaymentAdapter.getPayment(USER_ID, TRANSACTION_KEY);
                assertThat(result.transactionKey()).isEqualTo("txn-001");
            }

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            System.out.println("[Step 4-5] 2.5초 대기 → HALF_OPEN → 3번 성공 → " + cb.getState());
            System.out.println("→ 체감: \"서버가 살아나면 자동으로 복구된다\"");
        }
    }

    @Nested
    @DisplayName("4-3. pg-command / pg-query 독립성")
    class InstanceIndependence {

        @Test
        @DisplayName("pg-command CB가 OPEN이어도 pg-query는 정상 동작")
        void command_OPEN_이어도_query_정상() {
            CircuitBreaker commandCb = circuitBreakerRegistry.circuitBreaker("pg-command");
            CircuitBreaker queryCb = circuitBreakerRegistry.circuitBreaker("pg-query");

            // Step 1: pg-command CB → OPEN
            stubFor(post(urlEqualTo("/api/v1/payments"))
                    .willReturn(aResponse().withStatus(500)));

            for (int i = 0; i < 5; i++) {
                pgPaymentAdapter.requestPayment(USER_ID, PG_REQUEST);
            }
            assertThat(commandCb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
            System.out.println("[pg-command] 상태: " + commandCb.getState());

            // Step 2: pg-query는 영향 없음
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withBody(PG_SUCCESS_RESPONSE)));

            PaymentInfo result = pgPaymentAdapter.getPayment(USER_ID, TRANSACTION_KEY);

            assertThat(result.transactionKey()).isEqualTo("txn-001");
            assertThat(queryCb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

            System.out.println("[pg-query] 상태: " + queryCb.getState());
            System.out.println("[pg-query] 결과: " + result);
            System.out.println("→ 체감: \"결제가 죽어도 조회는 된다. 인스턴스 분리의 가치\"");
        }
    }
}
