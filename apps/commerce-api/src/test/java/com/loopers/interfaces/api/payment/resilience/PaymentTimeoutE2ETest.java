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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class PaymentTimeoutE2ETest extends PaymentResilienceTestBase {

    @Nested
    class PG_타임아웃 {

        @Test
        void 타임아웃시_결제_상태가_PENDING으로_유지된다() {
            setChaosToss("TIMEOUT");

            Long orderId = createOrderForPayment();
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response =
                    requestPaymentWithOrder(orderId, PgType.TOSS);

            // readTimeout(5s) 발동 → fallback → 500 응답, DB 상태는 PENDING 유지
            Payment payment = getPaymentByOrderId(orderId);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR),
                    () -> assertThat(payment).isNotNull(),
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING)
            );
        }

        @Test
        void PENDING_상태에서_PG_복구_후_verify로_미완료_확인시_FAILED로_전이된다() {
            // 1단계: 타임아웃으로 PENDING 생성
            setChaosToss("TIMEOUT");
            Long orderId = createOrderForPayment();
            requestPaymentWithOrder(orderId, PgType.TOSS);

            Payment pendingPayment = getPaymentByOrderId(orderId);
            assertThat(pendingPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);

            // 2단계: PG 복구 + 서킷 리셋 (타임아웃이 서킷에 실패로 집계되었을 수 있음)
            setChaosToss("NORMAL");
            circuitBreakerRegistry.getAllCircuitBreakers()
                    .forEach(io.github.resilience4j.circuitbreaker.CircuitBreaker::reset);

            // 3단계: verify 호출 — mock-toss에 해당 결제 없음 (TIMEOUT으로 미생성) → 404 → FAILED
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    PAYMENT_ENDPOINT + "/" + pendingPayment.getId() + "/verify",
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
