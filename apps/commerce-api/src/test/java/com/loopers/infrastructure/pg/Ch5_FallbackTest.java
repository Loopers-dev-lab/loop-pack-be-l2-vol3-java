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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = Ch5_FallbackTest.TestConfig.class)
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
@DisplayName("Ch5. Fallback — 실패해도 서비스는 살린다")
class Ch5_FallbackTest {

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

    @BeforeEach
    void setUp() {
        resetAllRequests();
        resetAllScenarios();
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @Nested
    @DisplayName("5-1. Fallback 없음 vs 있음")
    class FallbackComparison {

        @Test
        @DisplayName("Feign 직접 호출 (command) — 500 에러가 FeignException으로 그대로 전달")
        void feign_command_직접호출_FeignException_전달() {
            stubFor(post(urlEqualTo("/api/v1/payments"))
                    .willReturn(aResponse().withStatus(500)));

            assertThatThrownBy(() -> pgCommandClient.requestPayment(USER_ID, FEIGN_REQUEST))
                    .isInstanceOf(FeignException.InternalServerError.class)
                    .satisfies(ex -> {
                        System.out.println("[Feign 직접 호출 - command] 예외: " + ex.getClass().getSimpleName());
                        System.out.println("[Feign 직접 호출 - command] 메시지: " + ex.getMessage());
                        System.out.println("→ 기술적이고 불친절한 에러 메시지가 사용자에게 노출될 수 있다");
                    });
        }

        @Test
        @DisplayName("Adapter 호출 (command) — fallback으로 PaymentInfo.empty() 반환")
        void adapter_command_호출_CoreException_전달() {
            stubFor(post(urlEqualTo("/api/v1/payments"))
                    .willReturn(aResponse().withStatus(500)));

            PaymentInfo result = pgPaymentAdapter.requestPayment(USER_ID, PG_REQUEST);

            assertThat(result).isEqualTo(PaymentInfo.empty());

            System.out.println("[Adapter 호출 - command] 결과: " + result);
            System.out.println("→ fallback으로 PaymentInfo.empty() 반환, 서비스 계속 동작");
        }

        @Test
        @DisplayName("Feign 직접 호출 (query) — 500 에러가 FeignException으로 전달")
        void feign_query_직접호출_FeignException_전달() {
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .willReturn(aResponse().withStatus(500)));

            assertThatThrownBy(() -> pgQueryClient.getPayment(USER_ID, TRANSACTION_KEY))
                    .isInstanceOf(FeignException.InternalServerError.class)
                    .satisfies(ex -> {
                        System.out.println("[Feign 직접 호출 - query] 예외: " + ex.getClass().getSimpleName());
                        System.out.println("[Feign 직접 호출 - query] 메시지: " + ex.getMessage());
                        System.out.println("→ 조회 실패 시 전체 서비스가 에러를 반환하게 된다");
                    });
        }

        @Test
        @DisplayName("Adapter 호출 (query) — fallback으로 PaymentInfo.empty() 반환, 서비스 계속 동작")
        void adapter_query_호출_empty_반환_서비스_유지() {
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .willReturn(aResponse().withStatus(500)));

            PaymentInfo result = pgPaymentAdapter.getPayment(USER_ID, TRANSACTION_KEY);

            assertThat(result).isEqualTo(PaymentInfo.empty());

            System.out.println("[Adapter 호출 - query] 결과: " + result);
            System.out.println("→ 빈 데이터라도 반환하여 서비스가 계속 동작한다");

            System.out.println("\n=== Fallback 비교 요약 ===");
            System.out.println("직접 호출: FeignException(status 500, ...) — 기술적 에러");
            System.out.println("Adapter (결제): CoreException(결제 서비스에 일시적인 문제가 발생했습니다.) — 사용자 메시지");
            System.out.println("Adapter (조회): PaymentInfo.empty() — 서비스 계속 동작");
            System.out.println("→ 체감: \"에러를 그대로 보여주는 것과 대안을 주는 것의 차이\"");
        }
    }

    @Nested
    @DisplayName("5-2. Fallback이 다른 장애를 일으키지 않는지 확인")
    class FallbackSafety {

        @Test
        @DisplayName("empty() 반환값의 모든 필드가 null이고, NPE 없이 안전하게 접근 가능")
        void empty_반환값_모든_필드_null_NPE_없이_접근_가능() {
            stubFor(get(urlPathEqualTo("/api/v1/payments/" + TRANSACTION_KEY))
                    .willReturn(aResponse().withStatus(500)));

            PaymentInfo result = pgPaymentAdapter.getPayment(USER_ID, TRANSACTION_KEY);

            // 모든 필드가 null인지 확인
            assertThat(result.transactionKey()).isNull();
            assertThat(result.orderId()).isNull();
            assertThat(result.cardType()).isNull();
            assertThat(result.cardNo()).isNull();
            assertThat(result.amount()).isNull();
            assertThat(result.status()).isNull();

            // NPE 없이 안전하게 필드 접근 가능 확인
            assertThatCode(() -> {
                String txnKey = result.transactionKey();
                String orderId = result.orderId();
                String cardType = result.cardType();
                String cardNo = result.cardNo();
                String amount = result.amount();
                Object status = result.status();

                // null-safe 연산도 문제없이 동작
                boolean hasTxn = txnKey != null;
                boolean hasOrder = orderId != null;
            }).doesNotThrowAnyException();

            System.out.println("[Fallback 안전성] empty() 필드 접근 — NPE 없음");
            System.out.println("  transactionKey: " + result.transactionKey());
            System.out.println("  orderId: " + result.orderId());
            System.out.println("  cardType: " + result.cardType());
            System.out.println("  cardNo: " + result.cardNo());
            System.out.println("  amount: " + result.amount());
            System.out.println("  status: " + result.status());
            System.out.println("→ 체감: \"빈 응답이 2차 장애를 일으키지 않는다\"");
        }
    }
}
