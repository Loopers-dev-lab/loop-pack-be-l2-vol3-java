package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentDomainTest {

    private static final String MEMBER_ID = "member-1";
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String CARD_NO = "1234-5678-1234-5678";

    @Nested
    @DisplayName("결제 생성")
    class Create {

        @Test
        @DisplayName("유효한 값으로 결제를 생성하면 REQUESTED 상태로 생성된다")
        void create_ValidInput_ReturnsRequestedStatus() {
            Payment payment = new Payment(MEMBER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, 5000);

            assertThat(payment.status()).isEqualTo(PaymentStatus.REQUESTED);
            assertThat(payment.memberId()).isEqualTo(MEMBER_ID);
            assertThat(payment.orderId()).isEqualTo(ORDER_ID);
            assertThat(payment.amount()).isEqualTo(5000);
        }

        @Test
        @DisplayName("결제 금액이 0 이하면 BAD_REQUEST 예외가 발생한다")
        void create_AmountNotPositive_ThrowsBadRequest() {
            assertThatThrownBy(() -> new Payment(MEMBER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, 0))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("카드 번호 형식이 올바르지 않으면 BAD_REQUEST 예외가 발생한다")
        void create_InvalidCardNoFormat_ThrowsBadRequest() {
            assertThatThrownBy(() -> new Payment(MEMBER_ID, ORDER_ID, CardType.SAMSUNG, "1234567812345678", 5000))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("SUCCEEDED 상태 결제에 거래 키가 없으면 BAD_REQUEST 예외가 발생한다")
        void create_SucceededWithoutTransactionKey_ThrowsBadRequest() {
            assertThatThrownBy(() -> new Payment(
                    UUID.randomUUID(),
                    MEMBER_ID,
                    ORDER_ID,
                    CardType.SAMSUNG,
                    CARD_NO,
                    5000,
                    PaymentStatus.SUCCEEDED,
                    null,
                    null,
                    null,
                    null,
                    null
            ))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("CANCEL_FAILED 상태 결제에 실패 사유가 없으면 BAD_REQUEST 예외가 발생한다")
        void create_CancelFailedWithoutReason_ThrowsBadRequest() {
            assertThatThrownBy(() -> new Payment(
                    UUID.randomUUID(),
                    MEMBER_ID,
                    ORDER_ID,
                    CardType.SAMSUNG,
                    CARD_NO,
                    5000,
                    PaymentStatus.CANCEL_FAILED,
                    "TRX-1",
                    "  ",
                    null,
                    null,
                    null
            ))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("CANCEL_RECONCILE_REQUIRED 상태 결제에 사유가 없으면 BAD_REQUEST 예외가 발생한다")
        void create_CancelReconcileRequiredWithoutReason_ThrowsBadRequest() {
            assertThatThrownBy(() -> new Payment(
                    UUID.randomUUID(),
                    MEMBER_ID,
                    ORDER_ID,
                    CardType.SAMSUNG,
                    CARD_NO,
                    5000,
                    PaymentStatus.CANCEL_RECONCILE_REQUIRED,
                    "TRX-1",
                    "  ",
                    null,
                    null,
                    null
            ))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @Nested
    @DisplayName("결제 처리")
    class Handle {

        @Test
        @DisplayName("REQUESTED 상태 결제를 승인하면 SUCCEEDED 상태가 된다")
        void markSucceeded_RequestedPayment_ChangesStatusToSucceeded() {
            Payment payment = new Payment(MEMBER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, 5000);

            Payment succeeded = payment.markSucceeded("TRX-1");

            assertThat(succeeded.status()).isEqualTo(PaymentStatus.SUCCEEDED);
            assertThat(succeeded.pgTransactionKey()).isEqualTo("TRX-1");
        }

        @Test
        @DisplayName("승인 거래 키가 비어 있으면 BAD_REQUEST 예외가 발생한다")
        void markSucceeded_BlankTransactionKey_ThrowsBadRequest() {
            Payment payment = new Payment(MEMBER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, 5000);

            assertThatThrownBy(() -> payment.markSucceeded(" "))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("REQUESTED 상태 결제를 실패 처리하면 FAILED 상태가 된다")
        void markFailed_RequestedPayment_ChangesStatusToFailed() {
            Payment payment = new Payment(MEMBER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, 5000);

            Payment failed = payment.markFailed("한도 초과");

            assertThat(failed.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(failed.reason()).isEqualTo("한도 초과");
        }

        @Test
        @DisplayName("실패 사유가 비어 있으면 BAD_REQUEST 예외가 발생한다")
        void markFailed_BlankReason_ThrowsBadRequest() {
            Payment payment = new Payment(MEMBER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, 5000);

            assertThatThrownBy(() -> payment.markFailed(" "))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("SUCCEEDED 상태에서 다시 승인하면 CONFLICT 예외가 발생한다")
        void markSucceeded_SucceededPayment_ThrowsConflict() {
            Payment payment = new Payment(MEMBER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, 5000)
                    .markSucceeded("TRX-1");

            assertThatThrownBy(() -> payment.markSucceeded("TRX-2"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }

        @Test
        @DisplayName("SUCCEEDED 상태에서 실패 처리하면 CONFLICT 예외가 발생한다")
        void markFailed_SucceededPayment_ThrowsConflict() {
            Payment payment = new Payment(MEMBER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, 5000)
                    .markSucceeded("TRX-1");

            assertThatThrownBy(() -> payment.markFailed("카드 오류"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }
    }

    @Nested
    @DisplayName("결제 취소")
    class Cancel {

        @Test
        @DisplayName("SUCCEEDED 상태에서 취소 요청하면 CANCEL_REQUESTED 상태가 된다")
        void requestCancel_SucceededPayment_ChangesStatusToCancelRequested() {
            Payment payment = new Payment(MEMBER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, 5000)
                    .markSucceeded("TRX-1");

            Payment cancelRequested = payment.requestCancel();

            assertThat(cancelRequested.status()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        }

        @Test
        @DisplayName("FAILED 상태에서 취소 요청하면 CONFLICT 예외가 발생한다")
        void requestCancel_FailedPayment_ThrowsConflict() {
            Payment payment = new Payment(MEMBER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, 5000)
                    .markFailed("카드 오류");

            assertThatThrownBy(payment::requestCancel)
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }

        @Test
        @DisplayName("CANCEL_REQUESTED 상태에서 취소 확정하면 CANCELLED 상태가 된다")
        void markCancelled_CancelRequestedPayment_ChangesStatusToCancelled() {
            Payment payment = new Payment(MEMBER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, 5000)
                    .markSucceeded("TRX-1")
                    .requestCancel();

            Payment cancelled = payment.markCancelled();

            assertThat(cancelled.status()).isEqualTo(PaymentStatus.CANCELLED);
        }

        @Test
        @DisplayName("CANCEL_REQUESTED 상태에서 취소 실패 처리하면 CANCEL_FAILED 상태가 된다")
        void markCancelFailed_CancelRequestedPayment_ChangesStatusToCancelFailed() {
            Payment payment = new Payment(MEMBER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, 5000)
                    .markSucceeded("TRX-1")
                    .requestCancel();

            Payment cancelFailed = payment.markCancelFailed("PG timeout");

            assertThat(cancelFailed.status()).isEqualTo(PaymentStatus.CANCEL_FAILED);
            assertThat(cancelFailed.reason()).isEqualTo("PG timeout");
        }

        @Test
        @DisplayName("CANCEL_FAILED 상태에서 재취소 요청하면 CANCEL_REQUESTED 상태로 전이된다")
        void requestCancel_CancelFailedPayment_ChangesStatusToCancelRequested() {
            Payment payment = new Payment(MEMBER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, 5000)
                    .markSucceeded("TRX-1")
                    .requestCancel()
                    .markCancelFailed("PG timeout");

            Payment retried = payment.requestCancel();

            assertThat(retried.status()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        }

        @Test
        @DisplayName("CANCELLED 상태에서 취소 요청하면 CONFLICT 예외가 발생한다")
        void requestCancel_CancelledPayment_ThrowsConflict() {
            Payment payment = new Payment(MEMBER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, 5000)
                    .markSucceeded("TRX-1")
                    .requestCancel()
                    .markCancelled();

            assertThatThrownBy(payment::requestCancel)
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }

        @Test
        @DisplayName("SUCCEEDED 상태에서 바로 취소 확정하면 CONFLICT 예외가 발생한다")
        void markCancelled_SucceededPayment_ThrowsConflict() {
            Payment payment = new Payment(MEMBER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, 5000)
                    .markSucceeded("TRX-1");

            assertThatThrownBy(payment::markCancelled)
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }

        @Test
        @DisplayName("취소 실패 사유가 비어 있으면 BAD_REQUEST 예외가 발생한다")
        void markCancelFailed_BlankReason_ThrowsBadRequest() {
            Payment payment = new Payment(MEMBER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, 5000)
                    .markSucceeded("TRX-1")
                    .requestCancel();

            assertThatThrownBy(() -> payment.markCancelFailed(" "))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("CANCEL_REQUESTED 상태에서 재처리 대기 전환하면 CANCEL_RECONCILE_REQUIRED 상태가 된다")
        void markCancelReconcileRequired_CancelRequestedPayment_ChangesStatus() {
            Payment payment = new Payment(MEMBER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, 5000)
                    .markSucceeded("TRX-1")
                    .requestCancel();

            Payment reconcileRequired = payment.markCancelReconcileRequired("조회 불가");

            assertThat(reconcileRequired.status()).isEqualTo(PaymentStatus.CANCEL_RECONCILE_REQUIRED);
            assertThat(reconcileRequired.reason()).isEqualTo("조회 불가");
        }
    }
}
