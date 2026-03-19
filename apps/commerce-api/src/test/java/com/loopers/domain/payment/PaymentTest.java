package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentTest {

    private static final Long ORDER_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final int AMOUNT = 50000;
    private static final String CARD_TYPE = "SAMSUNG";
    private static final String CARD_NO = "4111-1111-1111-1111";
    private static final String TRANSACTION_KEY = "20260319:TR:abc123";

    private Payment validPayment() {
        return new Payment(ORDER_ID, USER_ID, AMOUNT, CARD_TYPE, CARD_NO);
    }

    @DisplayName("Payment 생성 시")
    @Nested
    class Create {

        @DisplayName("정상적인 파라미터로 Payment가 생성되고, 초기 상태는 PENDING이다.")
        @Test
        void createsPayment_whenValidParameters() {
            // act
            Payment payment = validPayment();

            // assert
            assertThat(payment.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(payment.getUserId()).isEqualTo(USER_ID);
            assertThat(payment.getAmount()).isEqualTo(AMOUNT);
            assertThat(payment.getCardType()).isEqualTo(CARD_TYPE);
            assertThat(payment.getCardNo()).isEqualTo(CARD_NO);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getTransactionKey()).isNull();
            assertThat(payment.getFailureReason()).isNull();
        }

        @DisplayName("orderId가 null이면 BAD_REQUEST 에러가 발생한다.")
        @Test
        void throwsBadRequest_whenOrderIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> new Payment(null, USER_ID, AMOUNT, CARD_TYPE, CARD_NO));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("userId가 null이면 BAD_REQUEST 에러가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> new Payment(ORDER_ID, null, AMOUNT, CARD_TYPE, CARD_NO));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("amount가 0 이하이면 BAD_REQUEST 에러가 발생한다.")
        @Test
        void throwsBadRequest_whenAmountIsZeroOrNegative() {
            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> new Payment(ORDER_ID, USER_ID, 0, CARD_TYPE, CARD_NO));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("cardType이 blank이면 BAD_REQUEST 에러가 발생한다.")
        @Test
        void throwsBadRequest_whenCardTypeIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> new Payment(ORDER_ID, USER_ID, AMOUNT, "  ", CARD_NO));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("cardNo가 blank이면 BAD_REQUEST 에러가 발생한다.")
        @Test
        void throwsBadRequest_whenCardNoIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> new Payment(ORDER_ID, USER_ID, AMOUNT, CARD_TYPE, "  "));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("Payment 상태 전이 시")
    @Nested
    class StatusTransition {

        @DisplayName("PENDING 상태에서 markSuccess()를 호출하면 SUCCESS로 전이된다.")
        @Test
        void markSuccess_whenRequested() {
            // arrange
            Payment payment = validPayment();

            // act
            payment.markSuccess(TRANSACTION_KEY);

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.getTransactionKey()).isEqualTo(TRANSACTION_KEY);
        }

        @DisplayName("PENDING 상태에서 markFailed()를 호출하면 FAILED로 전이된다.")
        @Test
        void markFailed_whenRequested() {
            // arrange
            Payment payment = validPayment();
            String reason = "잔액 부족";

            // act
            payment.markFailed(TRANSACTION_KEY, reason);

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getTransactionKey()).isEqualTo(TRANSACTION_KEY);
            assertThat(payment.getFailureReason()).isEqualTo(reason);
        }

        @DisplayName("PENDING 상태에서 markTimeout()를 호출하면 TIMEOUT으로 전이된다.")
        @Test
        void markTimeout_whenRequested() {
            // arrange
            Payment payment = validPayment();

            // act
            payment.markTimeout();

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.TIMEOUT);
        }

        @DisplayName("SUCCESS 상태에서 markSuccess()를 호출하면 BAD_REQUEST 에러가 발생한다.")
        @Test
        void throwsBadRequest_whenAlreadySuccess() {
            // arrange
            Payment payment = validPayment();
            payment.markSuccess(TRANSACTION_KEY);

            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> payment.markSuccess(TRANSACTION_KEY));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("FAILED 상태에서 markSuccess()를 호출하면 BAD_REQUEST 에러가 발생한다.")
        @Test
        void throwsBadRequest_whenFailedToMarkSuccess() {
            // arrange
            Payment payment = validPayment();
            payment.markFailed(TRANSACTION_KEY, "잔액 부족");

            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> payment.markSuccess(TRANSACTION_KEY));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("Payment.isTerminal() 시")
    @Nested
    class IsTerminal {

        @DisplayName("PENDING 상태이면 false를 반환한다.")
        @Test
        void returnsFalse_whenRequested() {
            // arrange
            Payment payment = validPayment();

            // act & assert
            assertThat(payment.isTerminal()).isFalse();
        }

        @DisplayName("SUCCESS 상태이면 true를 반환한다.")
        @Test
        void returnsTrue_whenSuccess() {
            // arrange
            Payment payment = validPayment();
            payment.markSuccess(TRANSACTION_KEY);

            // act & assert
            assertThat(payment.isTerminal()).isTrue();
        }

        @DisplayName("FAILED 상태이면 true를 반환한다.")
        @Test
        void returnsTrue_whenFailed() {
            // arrange
            Payment payment = validPayment();
            payment.markFailed(TRANSACTION_KEY, "잔액 부족");

            // act & assert
            assertThat(payment.isTerminal()).isTrue();
        }

        @DisplayName("TIMEOUT 상태이면 true를 반환한다.")
        @Test
        void returnsTrue_whenTimeout() {
            // arrange
            Payment payment = validPayment();
            payment.markTimeout();

            // act & assert
            assertThat(payment.isTerminal()).isTrue();
        }
    }

    @DisplayName("Payment.assignTransactionKey() 시")
    @Nested
    class AssignTransactionKey {

        @DisplayName("PG에서 받은 transactionKey를 저장한다.")
        @Test
        void assignsTransactionKey() {
            // arrange
            Payment payment = validPayment();

            // act
            payment.assignTransactionKey(TRANSACTION_KEY);

            // assert
            assertThat(payment.getTransactionKey()).isEqualTo(TRANSACTION_KEY);
        }
    }
}
