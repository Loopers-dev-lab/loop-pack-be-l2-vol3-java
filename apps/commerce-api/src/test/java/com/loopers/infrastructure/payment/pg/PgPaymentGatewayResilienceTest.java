package com.loopers.infrastructure.payment.pg;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGateway.TransactionResult;
import com.loopers.domain.payment.PaymentGatewayRetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "resilience4j.circuitbreaker.instances.pgCircuit.sliding-window-size=3",
        "resilience4j.circuitbreaker.instances.pgCircuit.minimum-number-of-calls=3",
        "resilience4j.circuitbreaker.instances.pgCircuit.wait-duration-in-open-state=1s",
        "resilience4j.circuitbreaker.instances.pgCircuit.permitted-number-of-calls-in-half-open-state=1",
        "resilience4j.retry.instances.pgRetry.max-attempts=2",
        "resilience4j.retry.instances.pgRetry.wait-duration=100ms",
        "resilience4j.retry.instances.pgRetry.enable-exponential-backoff=false",
        "resilience4j.retry.instances.pgRetry.enable-randomized-wait=false"
    }
)
@DisplayName("PG PaymentGateway Resilience 통합 테스트")
class PgPaymentGatewayResilienceTest {

    @Autowired
    private PaymentGateway paymentGateway;

    @Autowired
    @Qualifier("pgRestTemplate")
    private RestTemplate pgRestTemplate;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        mockServer = MockRestServiceServer.bindTo(pgRestTemplate).build();
        circuitBreakerRegistry.circuitBreaker("pgCircuit").reset();
    }

    @DisplayName("서킷 브레이커가 동작할 때, ")
    @Nested
    class CircuitBreaker {

        @DisplayName("연속 실패 후 OPEN 상태가 되면, CallNotPermittedException이 발생한다.")
        @Test
        void throwsCallNotPermitted_whenCircuitOpens() {
            // 3회 연속 5xx → 서킷 OPEN
            mockServer.expect(ExpectedCount.times(3), requestTo("http://localhost:8082/api/v1/payments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

            // 3회 실패시키기
            for (int i = 0; i < 3; i++) {
                try {
                    paymentGateway.requestPayment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000);
                } catch (Exception ignored) {
                }
            }
            mockServer.verify();

            // 4번째 호출 → 서킷 OPEN → CallNotPermittedException
            assertThatThrownBy(() ->
                paymentGateway.requestPayment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000)
            ).isInstanceOf(CallNotPermittedException.class);
        }
    }

    @DisplayName("Retry가 동작할 때, ")
    @Nested
    class Retry {

        @DisplayName("getTransactionStatus에서 첫 호출 5xx 후 재시도 성공하면, 결과를 반환한다.")
        @Test
        void retriesAndSucceeds_whenFirstCallFails() {
            String successBody = """
                {
                    "meta": {"result": "SUCCESS", "errorCode": null, "message": null},
                    "data": {
                        "transactionKey": "TR:test123",
                        "orderId": "000001",
                        "cardType": "SAMSUNG",
                        "cardNo": "1234-****-****-3456",
                        "amount": 50000,
                        "status": "SUCCESS",
                        "reason": "정상 승인"
                    }
                }
                """;

            // 첫 호출: 5xx, 재시도: 성공
            mockServer.expect(ExpectedCount.once(), requestTo("http://localhost:8082/api/v1/payments/TR:test123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

            mockServer.expect(ExpectedCount.once(), requestTo("http://localhost:8082/api/v1/payments/TR:test123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(successBody, MediaType.APPLICATION_JSON));

            TransactionResult result = paymentGateway.getTransactionStatus(1L, "TR:test123");

            assertThat(result.status()).isEqualTo("SUCCESS");
            mockServer.verify();
        }
    }

    @DisplayName("requestPayment에는 Retry가 적용되지 않을 때, ")
    @Nested
    class NoRetryOnRequest {

        @DisplayName("5xx 발생 시 1회만 호출되고 예외가 발생한다.")
        @Test
        void doesNotRetry_whenRequestPaymentFails() {
            // 1회만 기대 (Retry 없음)
            mockServer.expect(ExpectedCount.once(), requestTo("http://localhost:8082/api/v1/payments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

            assertThatThrownBy(() ->
                paymentGateway.requestPayment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000)
            ).isInstanceOf(PaymentGatewayRetryableException.class);

            mockServer.verify(); // 1회만 호출됐는지 확인
        }
    }
}
