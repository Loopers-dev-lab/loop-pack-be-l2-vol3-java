package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentModelTest {

    @Nested
    @DisplayName("결제 생성")
    class Create {

        @DisplayName("U1-1: Payment 생성 시 초기 상태는 REQUESTED이다")
        @Test
        void create_initialStatusIsRequested() {
            PaymentModel payment = PaymentModel.create(1L, 5000, "SAMSUNG", "1234-5678-9012-3456");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
            assertThat(payment.getOrderId()).isEqualTo(1L);
            assertThat(payment.getAmount()).isEqualTo(5000);
            assertThat(payment.getCardType()).isEqualTo("SAMSUNG");
            assertThat(payment.getCardNo()).isEqualTo("1234-5678-9012-3456");
            assertThat(payment.getTransactionKey()).isNull();
            assertThat(payment.getPgProvider()).isNull();
            assertThat(payment.getFailureReason()).isNull();
        }
    }

    @Nested
    @DisplayName("상태 전이")
    class StatusTransition {

        @DisplayName("U1-2: REQUESTED → PENDING 전이 성공")
        @Test
        void requested_toPending_succeeds() {
            PaymentModel payment = PaymentModel.create(1L, 5000, "SAMSUNG", "1234-5678-9012-3456");

            payment.markPending("TX-001", "SIMULATOR");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getTransactionKey()).isEqualTo("TX-001");
            assertThat(payment.getPgProvider()).isEqualTo("SIMULATOR");
        }

        @DisplayName("U1-3: PENDING → PAID 전이 성공")
        @Test
        void pending_toPaid_succeeds() {
            PaymentModel payment = PaymentModel.create(1L, 5000, "SAMSUNG", "1234-5678-9012-3456");
            payment.markPending("TX-001", "SIMULATOR");

            payment.markPaid();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        }

        @DisplayName("U1-4: PENDING → FAILED 전이 성공")
        @Test
        void pending_toFailed_succeeds() {
            PaymentModel payment = PaymentModel.create(1L, 5000, "SAMSUNG", "1234-5678-9012-3456");
            payment.markPending("TX-001", "SIMULATOR");

            payment.markFailed("한도초과입니다.");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getFailureReason()).isEqualTo("한도초과입니다.");
        }

        @DisplayName("U1-5: PAID → FAILED 전이 불가 (예외)")
        @Test
        void paid_toFailed_throwsException() {
            PaymentModel payment = PaymentModel.create(1L, 5000, "SAMSUNG", "1234-5678-9012-3456");
            payment.markPending("TX-001", "SIMULATOR");
            payment.markPaid();

            assertThatThrownBy(() -> payment.markFailed("테스트"))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("U1-6: FAILED → PAID 전이 불가 (예외)")
        @Test
        void failed_toPaid_throwsException() {
            PaymentModel payment = PaymentModel.create(1L, 5000, "SAMSUNG", "1234-5678-9012-3456");
            payment.markPending("TX-001", "SIMULATOR");
            payment.markFailed("실패");

            assertThatThrownBy(() -> payment.markPaid())
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("REQUESTED → FAILED 전이 성공 (PG 요청 자체 실패)")
        @Test
        void requested_toFailed_succeeds() {
            PaymentModel payment = PaymentModel.create(1L, 5000, "SAMSUNG", "1234-5678-9012-3456");

            payment.markFailed("PG 연결 실패");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getFailureReason()).isEqualTo("PG 연결 실패");
        }

        @DisplayName("REQUESTED → UNKNOWN 전이 성공 (PG 타임아웃)")
        @Test
        void requested_toUnknown_succeeds() {
            PaymentModel payment = PaymentModel.create(1L, 5000, "SAMSUNG", "1234-5678-9012-3456");

            payment.markUnknown();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        }

        @DisplayName("UNKNOWN → PAID 전이 성공 (PG 확인 후 성공)")
        @Test
        void unknown_toPaid_succeeds() {
            PaymentModel payment = PaymentModel.create(1L, 5000, "SAMSUNG", "1234-5678-9012-3456");
            payment.markUnknown();

            payment.markPaid();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        }

        @DisplayName("UNKNOWN → FAILED 전이 성공 (PG 확인 후 실패)")
        @Test
        void unknown_toFailed_succeeds() {
            PaymentModel payment = PaymentModel.create(1L, 5000, "SAMSUNG", "1234-5678-9012-3456");
            payment.markUnknown();

            payment.markFailed("PG 확인 결과 실패");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getFailureReason()).isEqualTo("PG 확인 결과 실패");
        }

        @DisplayName("PAID → UNKNOWN 전이 불가 (예외)")
        @Test
        void paid_toUnknown_throwsException() {
            PaymentModel payment = PaymentModel.create(1L, 5000, "SAMSUNG", "1234-5678-9012-3456");
            payment.markPending("TX-001", "SIMULATOR");
            payment.markPaid();

            assertThatThrownBy(() -> payment.markUnknown())
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
