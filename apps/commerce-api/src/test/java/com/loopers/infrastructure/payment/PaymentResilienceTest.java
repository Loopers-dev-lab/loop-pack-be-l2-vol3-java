package com.loopers.infrastructure.payment;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class PaymentResilienceTest {

    @Autowired
    private PgPaymentGateway pgPaymentGateway;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockitoBean
    private PgClient pgClient;

    @BeforeEach
    void resetCircuitBreaker() {
        var cb = circuitBreakerRegistry.circuitBreaker("pgCircuit");
        cb.reset();
    }

    private PgPaymentRequest createRequest() {
        return new PgPaymentRequest("100", "SAMSUNG", "1234-5678-9814-1451", 129000L,
                "http://localhost:8080/api/v1/payments/callback");
    }

    @DisplayName("CircuitBreaker 상태 전이")
    @Nested
    class CircuitBreakerStateTransition {

        @DisplayName("실패율이 50%를 넘으면 서킷이 열린다.")
        @Test
        void openWhenFailureRateExceeds() {
            // given - PG 호출이 항상 실패하도록 설정
            given(pgClient.requestPayment(any(), any()))
                    .willThrow(new RuntimeException("PG 장애"));

            var cb = circuitBreakerRegistry.circuitBreaker("pgCircuit");

            // when
            // Retry가 3번씩 시도하므로, 실제 pgClient 호출은 더 많이 발생함.
            for (int i = 0; i < 6; i++) {
                pgPaymentGateway.requestPayment("1", createRequest());
                // fallback 실행 -> 예외 발생 x
            }

            // then
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }

    }

    @DisplayName("Retry 동작")
    @Nested
    class RetryBehavior {

        @DisplayName("1차 실패 후 2차에 성공하면 정상 응답을 반환한다")
        @Test
        void successAfterRetry() {
            // given — 1차 실패, 2차 성공
            PgPaymentResponse successResponse = new PgPaymentResponse(
                    new PgPaymentResponse.Meta("SUCCESS", null, null),
                    new PgPaymentResponse.Data("TX-001", "PENDING")
            );
            given(pgClient.requestPayment(any(), any()))
                    .willThrow(new RuntimeException("timeout"))
                    .willReturn(successResponse);

            // when
            PgPaymentResponse result = pgPaymentGateway.requestPayment("1", createRequest());

            // then
            assertThat(result.data().transactionKey()).isEqualTo("TX-001");
            verify(pgClient, times(2)).requestPayment(any(), any());
        }
    }
}
