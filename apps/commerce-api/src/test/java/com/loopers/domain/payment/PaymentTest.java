package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentTest {

    private Payment createPendingPayment() {
        return new Payment(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000);
    }

    @DisplayName("Payment를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 정보이면, PENDING 상태로 생성된다.")
        @Test
        void createsPayment_whenValidInfo() {
            Payment payment = createPendingPayment();

            assertAll(
                () -> assertThat(payment.getOrderId()).isEqualTo(1L),
                () -> assertThat(payment.getUserId()).isEqualTo(100L),
                () -> assertThat(payment.getCardType()).isEqualTo(CardType.SAMSUNG),
                () -> assertThat(payment.getCardNo()).isEqualTo("1234-5678-9012-3456"),
                () -> assertThat(payment.getAmount()).isEqualTo(50000),
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(payment.getTransactionKey()).isNull()
            );
        }

        @DisplayName("orderId가 null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenOrderIdIsNull() {
            assertThrows(IllegalArgumentException.class,
                () -> new Payment(null, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", 50000));
        }

        @DisplayName("금액이 0 이하이면, 예외가 발생한다.")
        @Test
        void throwsException_whenAmountIsZeroOrNegative() {
            assertThrows(IllegalStateException.class,
                () -> new Payment(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", 0));
        }
    }

    @DisplayName("상태 전이: PENDING → IN_PROGRESS")
    @Nested
    class MarkInProgress {

        @DisplayName("PENDING 상태에서 transactionKey와 함께 IN_PROGRESS로 전환된다.")
        @Test
        void transitionsToInProgress_whenPending() {
            Payment payment = createPendingPayment();

            payment.markInProgress("20250316:TR:abc123");

            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS),
                () -> assertThat(payment.getTransactionKey()).isEqualTo("20250316:TR:abc123")
            );
        }

        @DisplayName("IN_PROGRESS 상태에서 다시 markInProgress를 호출하면, 예외가 발생한다.")
        @Test
        void throwsException_whenAlreadyInProgress() {
            Payment payment = createPendingPayment();
            payment.markInProgress("20250316:TR:abc123");

            CoreException result = assertThrows(CoreException.class,
                () -> payment.markInProgress("20250316:TR:def456"));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("transactionKey가 비어있으면, 예외가 발생한다.")
        @Test
        void throwsException_whenTransactionKeyIsBlank() {
            Payment payment = createPendingPayment();

            assertThrows(IllegalArgumentException.class,
                () -> payment.markInProgress(""));
        }
    }

    @DisplayName("상태 전이: IN_PROGRESS → PAID")
    @Nested
    class MarkPaid {

        @DisplayName("IN_PROGRESS 상태에서 결제 완료 처리된다.")
        @Test
        void transitionsToPaid_whenInProgress() {
            Payment payment = createPendingPayment();
            payment.markInProgress("20250316:TR:abc123");

            payment.markPaid();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        }

        @DisplayName("PENDING 상태에서 markPaid를 호출하면, 예외가 발생한다.")
        @Test
        void throwsException_whenPending() {
            Payment payment = createPendingPayment();

            CoreException result = assertThrows(CoreException.class, payment::markPaid);
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이미 PAID 상태에서 다시 markPaid를 호출하면, 예외가 발생한다.")
        @Test
        void throwsException_whenAlreadyPaid() {
            Payment payment = createPendingPayment();
            payment.markInProgress("20250316:TR:abc123");
            payment.markPaid();

            CoreException result = assertThrows(CoreException.class, payment::markPaid);
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("상태 전이: PENDING/IN_PROGRESS → FAILED")
    @Nested
    class MarkFailed {

        @DisplayName("IN_PROGRESS 상태에서 실패 사유와 함께 FAILED로 전환된다.")
        @Test
        void transitionsToFailed_whenInProgress() {
            Payment payment = createPendingPayment();
            payment.markInProgress("20250316:TR:abc123");

            payment.markFailed("한도초과입니다.");

            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(payment.getFailureReason()).isEqualTo("한도초과입니다.")
            );
        }

        @DisplayName("PENDING 상태에서 PG 미접수 확정 시 FAILED로 전환된다.")
        @Test
        void transitionsToFailed_whenPending() {
            Payment payment = createPendingPayment();

            payment.markFailed("PG 미접수 확인됨");

            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(payment.getFailureReason()).isEqualTo("PG 미접수 확인됨")
            );
        }

        @DisplayName("이미 PAID 상태에서 markFailed를 호출하면, 예외가 발생한다.")
        @Test
        void throwsException_whenAlreadyPaid() {
            Payment payment = createPendingPayment();
            payment.markInProgress("20250316:TR:abc123");
            payment.markPaid();

            CoreException result = assertThrows(CoreException.class,
                () -> payment.markFailed("한도초과"));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("전체 상태 흐름 테스트")
    @Nested
    class FullFlow {

        @DisplayName("PENDING → IN_PROGRESS → PAID 정상 흐름이 동작한다.")
        @Test
        void fullSuccessFlow() {
            Payment payment = createPendingPayment();

            payment.markInProgress("20250316:TR:abc123");
            payment.markPaid();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        }

        @DisplayName("PENDING → IN_PROGRESS → FAILED 실패 흐름이 동작한다.")
        @Test
        void fullFailureFlow() {
            Payment payment = createPendingPayment();

            payment.markInProgress("20250316:TR:abc123");
            payment.markFailed("잘못된 카드입니다.");

            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(payment.getFailureReason()).isEqualTo("잘못된 카드입니다.")
            );
        }

        @DisplayName("PAID 상태에서 FAILED로 전환할 수 없다.")
        @Test
        void cannotTransitionFromPaidToFailed() {
            Payment payment = createPendingPayment();
            payment.markInProgress("20250316:TR:abc123");
            payment.markPaid();

            assertThrows(CoreException.class,
                () -> payment.markFailed("한도초과"));
        }

        @DisplayName("FAILED 상태에서 PAID로 전환할 수 없다.")
        @Test
        void cannotTransitionFromFailedToPaid() {
            Payment payment = createPendingPayment();
            payment.markInProgress("20250316:TR:abc123");
            payment.markFailed("한도초과");

            assertThrows(CoreException.class, payment::markPaid);
        }
    }
}
