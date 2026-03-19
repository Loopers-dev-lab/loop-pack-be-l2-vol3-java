package com.loopers.interfaces.api.payment.resilience;

import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.gateway.PgType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.payment.PaymentV1Dto;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class PaymentCircuitBreakerE2ETest extends PaymentResilienceTestBase {

    @Nested
    class 서킷_OPEN_전이 {

        @Test
        void DEAD_모드에서_실패_누적시_서킷_OPEN으로_전이된다() {
            setChaosToss("DEAD");

            // minimumNumberOfCalls(5)만큼 실패 누적
            for (int i = 0; i < 5; i++) {
                requestPayment(PgType.TOSS);
            }

            assertThat(getCircuitBreakerState("toss-request"))
                    .isEqualTo(CircuitBreaker.State.OPEN);

            // 6번째 요청 — 서킷 OPEN 상태에서 fallback 응답
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = requestPayment(PgType.TOSS);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR),
                    () -> assertThat(response.getBody().meta().message()).contains("결제 서비스를 이용할 수 없습니다")
            );
        }

        @Test
        void slowCall_누적시_서킷_OPEN으로_전이된다() {
            // slowCallDurationThreshold: 2s, slowCallRateThreshold: 80%
            // TIME_BASED slidingWindowSize: 10초 → 5건이 10초 안에 완료되어야 함
            // 각 요청 ~2.1s × 5건 ≈ 10.5초 → 경계. mock 처리 포함 여유분 고려하여
            // slowMin/Max를 threshold 바로 위로 설정
            setChaosToss("SLOW", "slowMinMs=2050&slowMaxMs=2100");

            for (int i = 0; i < 5; i++) {
                requestPayment(PgType.TOSS);
            }

            // 5건 전부 slow(~2.1s > 2s) → slowCallRate 100% > 80% → OPEN
            assertThat(getCircuitBreakerState("toss-request"))
                    .isEqualTo(CircuitBreaker.State.OPEN);
        }
    }

    @Nested
    class 서킷_복구 {

        @Test
        @Tag("slow")
        void 서킷_OPEN에서_HALF_OPEN을_거쳐_CLOSED로_복구된다() throws InterruptedException {
            // 1단계: 서킷 OPEN
            setChaosToss("DEAD");
            for (int i = 0; i < 5; i++) {
                requestPayment(PgType.TOSS);
            }
            assertThat(getCircuitBreakerState("toss-request"))
                    .isEqualTo(CircuitBreaker.State.OPEN);

            // 2단계: PG 복구 + waitDurationInOpenState(30s) 대기
            setChaosToss("NORMAL");
            Thread.sleep(31_000);

            // automaticTransitionFromOpenToHalfOpenEnabled: true → 자동 HALF_OPEN
            assertThat(getCircuitBreakerState("toss-request"))
                    .isEqualTo(CircuitBreaker.State.HALF_OPEN);

            // 3단계: HALF_OPEN에서 permittedNumberOfCallsInHalfOpenState(3)만큼 성공 → CLOSED
            for (int i = 0; i < 3; i++) {
                ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = requestPayment(PgType.TOSS);
                assertAll(
                        () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                        () -> assertThat(response.getBody().data().status()).isEqualTo(PaymentStatus.SUCCEEDED)
                );
            }

            assertThat(getCircuitBreakerState("toss-request"))
                    .isEqualTo(CircuitBreaker.State.CLOSED);
        }
    }

    @Nested
    class PG_가용성 {

        @Test
        void 서킷_OPEN시_available_methods에서_해당_PG가_제외된다() {
            setChaosToss("DEAD");
            for (int i = 0; i < 5; i++) {
                requestPayment(PgType.TOSS);
            }

            ResponseEntity<ApiResponse<List<PgType>>> response = getAvailableMethods();

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data()).contains(PgType.NICE),
                    () -> assertThat(response.getBody().data()).doesNotContain(PgType.TOSS)
            );
        }

        @Test
        void 토스_장애시_나이스로_결제_성공한다() {
            // 토스 서킷 OPEN
            setChaosToss("DEAD");
            for (int i = 0; i < 5; i++) {
                requestPayment(PgType.TOSS);
            }
            assertThat(getCircuitBreakerState("toss-request"))
                    .isEqualTo(CircuitBreaker.State.OPEN);

            // 나이스로 결제 → 200 응답 (토스 장애와 무관하게 나이스 PG 호출 성공)
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = requestPayment(PgType.NICE);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data()).isNotNull(),
                    () -> assertThat(response.getBody().data().status()).isEqualTo(PaymentStatus.SUCCEEDED)
            );
        }
    }
}
