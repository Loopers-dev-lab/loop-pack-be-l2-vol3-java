package com.loopers.infrastructure.pg;

import com.loopers.fake.FakePgClient;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PgRouterTest {

    @Nested
    @DisplayName("결제 요청 라우팅")
    class RequestPayment {

        @DisplayName("U1-11: Primary PG 성공 → 즉시 반환")
        @Test
        void requestPayment_primarySuccess_returnsImmediately() {
            FakePgClient primary = new FakePgClient("SIMULATOR");
            FakePgClient fallback = new FakePgClient("TOSS");
            PgRouter router = new PgRouter(List.of(primary, fallback));

            PgPaymentRequest request = PgPaymentRequest.of(1L, "SAMSUNG", "1234-5678-9012-3456", 5000, "http://callback");

            PgPaymentResponse response = router.requestPayment(request);

            assertThat(response.status()).isEqualTo("PENDING");
            assertThat(response.transactionKey()).startsWith("TX-");
        }

        @DisplayName("U1-12: Primary PG 실패 → Fallback PG 시도")
        @Test
        void requestPayment_primaryFail_fallsBackToSecondary() {
            FakePgClient primary = new FakePgClient("SIMULATOR", true);  // 항상 실패
            FakePgClient fallback = new FakePgClient("TOSS");
            PgRouter router = new PgRouter(List.of(primary, fallback));

            PgPaymentRequest request = PgPaymentRequest.of(1L, "SAMSUNG", "1234-5678-9012-3456", 5000, "http://callback");

            PgPaymentResponse response = router.requestPayment(request);

            assertThat(response.status()).isEqualTo("PENDING");
            assertThat(response.transactionKey()).startsWith("TX-");
        }

        @DisplayName("U1-13: 모든 PG 실패 → CoreException 발생")
        @Test
        void requestPayment_allPgFail_throwsCoreException() {
            FakePgClient primary = new FakePgClient("SIMULATOR", true);
            FakePgClient fallback = new FakePgClient("TOSS", true);
            PgRouter router = new PgRouter(List.of(primary, fallback));

            PgPaymentRequest request = PgPaymentRequest.of(1L, "SAMSUNG", "1234-5678-9012-3456", 5000, "http://callback");

            assertThatThrownBy(() -> router.requestPayment(request))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.INTERNAL_ERROR);
        }
    }

    @Nested
    @DisplayName("PgRouter 생성")
    class Creation {

        @DisplayName("PG 클라이언트가 없으면 예외")
        @Test
        void creation_withEmptyList_throwsException() {
            assertThatThrownBy(() -> new PgRouter(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @DisplayName("null이면 예외")
        @Test
        void creation_withNull_throwsException() {
            assertThatThrownBy(() -> new PgRouter(null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Multi-PG Fallback + 타임아웃 규칙")
    class MultiPgFallback {

        @DisplayName("U6-3: Simulator 실패 → Toss 자동 전환 → SUCCESS")
        @Test
        void simulatorFail_fallbackToToss_success() {
            FakePgClient simulator = new FakePgClient("SIMULATOR");
            simulator.setShouldFail(true);
            FakePgClient toss = new FakePgClient("TOSS");
            toss.setResponseStatus("SUCCESS");

            PgRouter router = new PgRouter(List.of(simulator, toss));
            PgPaymentRequest request = PgPaymentRequest.of(1L, "SAMSUNG", "1234", 5000, "http://test");

            PgPaymentResponse response = router.requestPayment(request);

            assertThat(response.status()).isEqualTo("SUCCESS");
            assertThat(response.pgProvider()).isEqualTo("TOSS");
            assertThat(response.transactionKey()).isNotNull();
            assertThat(simulator.getCallCount()).isEqualTo(1);
            assertThat(toss.getCallCount()).isEqualTo(1);
        }

        @DisplayName("U6-4: Simulator 타임아웃 → Toss 전환하지 않음 → 예외 (중복 결제 방지)")
        @Test
        void simulatorTimeout_noFallback_throwsException() {
            FakePgClient simulator = new FakePgClient("SIMULATOR");
            simulator.setThrowTimeout(true);
            FakePgClient toss = new FakePgClient("TOSS");
            toss.setResponseStatus("SUCCESS");

            PgRouter router = new PgRouter(List.of(simulator, toss));
            PgPaymentRequest request = PgPaymentRequest.of(1L, "SAMSUNG", "1234", 5000, "http://test");

            assertThatThrownBy(() -> router.requestPayment(request))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("타임아웃");

            // Toss는 호출되지 않음 (중복 결제 방지)
            assertThat(toss.getCallCount()).isZero();
        }

        @DisplayName("Primary 성공 시 pgProvider 추적")
        @Test
        void primarySuccess_providerTracked() {
            FakePgClient simulator = new FakePgClient("SIMULATOR");
            FakePgClient toss = new FakePgClient("TOSS");

            PgRouter router = new PgRouter(List.of(simulator, toss));
            PgPaymentRequest request = PgPaymentRequest.of(1L, "SAMSUNG", "1234", 5000, "http://test");

            PgPaymentResponse response = router.requestPayment(request);

            assertThat(response.pgProvider()).isEqualTo("SIMULATOR");
            assertThat(toss.getCallCount()).isZero();
        }
    }

    @Nested
    @DisplayName("결제 상태 조회")
    class GetPaymentStatus {

        @DisplayName("pgProvider로 PG 찾아서 상태 조회 성공")
        @Test
        void getPaymentStatus_success() {
            FakePgClient primary = new FakePgClient("SIMULATOR");
            primary.registerStatus("TX-001",
                new PgPaymentStatusResponse("SUCCESS", "TX-001", "정상 승인되었습니다."));
            PgRouter router = new PgRouter(List.of(primary));

            PgPaymentStatusResponse response = router.getPaymentStatus("TX-001", "SIMULATOR");

            assertThat(response.status()).isEqualTo("SUCCESS");
            assertThat(response.transactionKey()).isEqualTo("TX-001");
        }

        @DisplayName("존재하지 않는 PG Provider → 예외")
        @Test
        void getPaymentStatus_unknownProvider_throwsException() {
            FakePgClient primary = new FakePgClient("SIMULATOR");
            PgRouter router = new PgRouter(List.of(primary));

            assertThatThrownBy(() -> router.getPaymentStatus("TX-001", "UNKNOWN"))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.INTERNAL_ERROR);
        }
    }
}
