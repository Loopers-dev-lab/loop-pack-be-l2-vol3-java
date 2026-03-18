package com.loopers.infrastructure.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@SpringBootTest
class PgPaymentGatewayCircuitBreakerTest {

    @Autowired
    private PgPaymentGateway pgPaymentGateway;

    @MockitoBean
    private PgClient pgClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        // 테스트 독립성: 매 테스트 전 서킷 초기화
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("pgCircuit");
        circuitBreaker.reset();

        // PgClient 호출 시 항상 예외 발생 (retry 대상 아닌 RuntimeException → retry 없이 즉시 실패)
        given(pgClient.requestPayment(anyString(), any()))
                .willThrow(new RuntimeException("PG 서버 연결 실패"));
    }

    @DisplayName("PG 호출이 연속으로 실패하면 서킷이 OPEN 상태로 전환된다.")
    @Test
    void opensCircuit_whenFailureRateExceedsThreshold() {
        // arrange
        // sliding-window-size: 10, minimum-number-of-calls: 10, failure-rate-threshold: 50
        // → 10번 중 6번 이상 실패 시 OPEN (여기서는 10번 모두 실패 = 100%)
        PgPaymentDto.PaymentRequest request = new PgPaymentDto.PaymentRequest("pgOrderCode-001", "SAMSUNG", "1234-5678-9012-3456", 10000L, "http://callback");

        // act: minimum-number-of-calls(10)만큼 호출 → 전부 실패 → fallback에서 PgPaymentException
        for (int i = 0; i < 10; i++) {
            try {
                pgPaymentGateway.requestPayment("user-1", request);
            } catch (PgPaymentException ignored) {
            }
        }

        // assert
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @DisplayName("서킷이 OPEN 상태이면 PgClient를 호출하지 않고 즉시 PgPaymentException이 발생한다.")
    @Test
    void throwsPgPaymentException_whenCircuitIsOpen() {
        // arrange: 서킷을 강제로 OPEN
        circuitBreaker.transitionToOpenState();
        PgPaymentDto.PaymentRequest request = new PgPaymentDto.PaymentRequest("pgOrderCode-002", "KB", "1234-5678-9012-3456", 10000L, "http://callback");

        // act & assert
        assertThatThrownBy(() -> pgPaymentGateway.requestPayment("user-1", request))
                .isInstanceOf(PgPaymentException.class);
        then(pgClient).shouldHaveNoInteractions();
    }
}
