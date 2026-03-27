package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PaymentModelTest {



    private PaymentModel createPendingPayment() {
        return new PaymentModel(1L, 100L, 50000, "VISA", "4111-1111-1111-1111");
    }

    @DisplayName("결제를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 정보면, PENDING 상태로 생성된다.")
        @Test
        void createSuccess() {
            // given & when
            PaymentModel payment = createPendingPayment();

            // then
            assertAll(
                    () -> assertThat(payment.getOrderId()).isEqualTo(1L),
                    () -> assertThat(payment.getMemberId()).isEqualTo(100L),
                    () -> assertThat(payment.getAmount()).isEqualTo(50000),
                    () -> assertThat(payment.getCardType()).isEqualTo("VISA"),
                    () -> assertThat(payment.getCardNo()).isEqualTo("4111-1111-1111-1111"),
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                    () -> assertThat(payment.getTransactionId()).isNull(),
                    () -> assertThat(payment.getFailReason()).isNull()
            );
        }

        @DisplayName("orderId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void failWithNullOrderId() {
            // given & when
            CoreException result = assertThrows(CoreException.class, () ->
                    new PaymentModel(null, 100L, 50000, "VISA", "4111-1111-1111-1111")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("memberId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void failWithNullMemberId() {
            // given & when
            CoreException result = assertThrows(CoreException.class, () ->
                    new PaymentModel(1L, null, 50000, "VISA", "4111-1111-1111-1111")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("amount가 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void failWithZeroAmount() {
            // given & when
            CoreException result = assertThrows(CoreException.class, () ->
                    new PaymentModel(1L, 100L, 0, "VISA", "4111-1111-1111-1111")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("cardType이 blank이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void failWithBlankCardType() {
            // given & when
            CoreException result = assertThrows(CoreException.class, () ->
                    new PaymentModel(1L, 100L, 50000, " ", "4111-1111-1111-1111")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("cardNo가 blank이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void failWithBlankCardNo() {
            // given & when
            CoreException result = assertThrows(CoreException.class, () ->
                    new PaymentModel(1L, 100L, 50000, "VISA", " ")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("결제 성공 처리할 때,")
    @Nested
    class MarkSuccess {

        @DisplayName("PENDING 상태면, SUCCESS로 전이되고 transactionId가 세팅된다.")
        @Test
        void successFromPending() {
            // given
            PaymentModel payment = createPendingPayment();

            // when
            payment.markSuccess("txn-001");

            // then
            assertAll(
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                    () -> assertThat(payment.getTransactionId()).isEqualTo("txn-001")
            );
        }

        @DisplayName("TIMED_OUT 상태면, SUCCESS로 전이된다.")
        @Test
        void successFromTimedOut() {
            // given
            PaymentModel payment = createPendingPayment();
            payment.markTimedOut("txn-001");

            // when
            payment.markSuccess("txn-001");

            // then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("FAILED 상태면, 예외가 발생한다.")
        @Test
        void failFromFailed() {
            // given
            PaymentModel payment = createPendingPayment();
            payment.markFailed("잔액 부족");

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    payment.markSuccess("txn-001")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("SUCCESS 상태면, 예외가 발생한다.")
        @Test
        void failFromSuccess() {
            // given
            PaymentModel payment = createPendingPayment();
            payment.markSuccess("txn-001");

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    payment.markSuccess("txn-002")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("결제 실패 처리할 때,")
    @Nested
    class MarkFailed {

        @DisplayName("PENDING 상태면, FAILED로 전이되고 failReason이 세팅된다.")
        @Test
        void failedFromPending() {
            // given
            PaymentModel payment = createPendingPayment();

            // when
            payment.markFailed("잔액 부족");

            // then
            assertAll(
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                    () -> assertThat(payment.getFailReason()).isEqualTo("잔액 부족")
            );
        }

        @DisplayName("TIMED_OUT 상태면, FAILED로 전이된다.")
        @Test
        void failedFromTimedOut() {
            // given
            PaymentModel payment = createPendingPayment();
            payment.markTimedOut("txn-001");

            // when
            payment.markFailed("PG 확인 결과 실패");

            // then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @DisplayName("SUCCESS 상태면, 예외가 발생한다.")
        @Test
        void failFromSuccess() {
            // given
            PaymentModel payment = createPendingPayment();
            payment.markSuccess("txn-001");

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    payment.markFailed("실패 사유")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("결제 타임아웃 처리할 때,")
    @Nested
    class MarkTimedOut {

        @DisplayName("PENDING 상태면, TIMED_OUT으로 전이되고 transactionId가 세팅된다.")
        @Test
        void timedOutFromPending() {
            // given
            PaymentModel payment = createPendingPayment();

            // when
            payment.markTimedOut("txn-001");

            // then
            assertAll(
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.TIMED_OUT),
                    () -> assertThat(payment.getTransactionId()).isEqualTo("txn-001")
            );
        }

        @DisplayName("SUCCESS 상태면, 예외가 발생한다.")
        @Test
        void failFromSuccess() {
            // given
            PaymentModel payment = createPendingPayment();
            payment.markSuccess("txn-001");

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    payment.markTimedOut("txn-001")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("FAILED 상태면, 예외가 발생한다.")
        @Test
        void failFromFailed() {
            // given
            PaymentModel payment = createPendingPayment();
            payment.markFailed("잔액 부족");

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    payment.markTimedOut("txn-001")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("TIMED_OUT 상태면, 예외가 발생한다.")
        @Test
        void failFromTimedOut() {
            // given
            PaymentModel payment = createPendingPayment();
            payment.markTimedOut("txn-001");

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    payment.markTimedOut("txn-002")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
