package com.loopers.interfaces.api.payment.resilience;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.gateway.PgType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.payment.PaymentV1Dto;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class PaymentTimeoutE2ETest extends PaymentResilienceTestBase {

    @Nested
    class PG_타임아웃 {

        @Test
        void 타임아웃_후_서킷_OPEN시_결제가_즉시_FAILED_처리된다() {
            setChaosToss("TIMEOUT");

            Long orderId = createOrderForPayment();
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response =
                    requestPaymentWithOrder(orderId, PgType.TOSS);

            Payment payment = getPaymentByOrderId(orderId);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR),
                    () -> assertThat(payment).isNotNull(),
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED)
            );
        }

        @Test
        void REQUESTED_상태에서_PG_복구_후_verify로_미완료_확인시_FAILED로_전이된다() {
            // 1단계: REQUESTED 상태의 Payment를 DB에 직접 생성
            Long orderId = createOrderForPayment();
            Payment payment = fixture.createRequestedPayment(orderId, 1L, new BigDecimal("50000"));
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);

            // 2단계: PG 정상 + 서킷 리셋
            setChaosToss("NORMAL");
            circuitBreakerRegistry.getAllCircuitBreakers()
                    .forEach(io.github.resilience4j.circuitbreaker.CircuitBreaker::reset);

            // 3단계: verify 호출 — mock-toss에 해당 결제 없음 → FAILED
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    PAYMENT_ENDPOINT + "/" + payment.getId() + "/verify",
                    HttpMethod.POST,
                    new HttpEntity<>(fixture.userHeaders(LOGIN_ID, PASSWORD)),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().status()).isEqualTo(PaymentStatus.FAILED)
            );
        }
    }
}
