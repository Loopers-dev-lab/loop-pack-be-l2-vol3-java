package com.loopers.domain.payment;

import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentModelTest {

    @DisplayName("결제 생성")
    @Nested
    class Create {

        @DisplayName("유효한 정보로 생성하면 상태가 PENDING이다")
        @Test
        void createsWithPendingStatus() {
            // given
            Long orderId = 1L;
            Long userId = 1L;
            CardType cardType = CardType.SAMSUNG;
            String maskedCardNo = "1234-****-****-1451";
            Money amount = new Money(50000);

            // when
            PaymentModel payment = new PaymentModel(orderId, userId, cardType, maskedCardNo, amount);

            // then
            assertAll(
                () -> assertThat(payment.orderId()).isEqualTo(orderId),
                () -> assertThat(payment.userId()).isEqualTo(userId),
                () -> assertThat(payment.cardType()).isEqualTo(CardType.SAMSUNG),
                () -> assertThat(payment.maskedCardNo()).isEqualTo(maskedCardNo),
                () -> assertThat(payment.amount()).isEqualTo(amount),
                () -> assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(payment.transactionKey()).isNull(),
                () -> assertThat(payment.failureReason()).isNull()
            );
        }

        @DisplayName("orderId가 null이면 예외가 발생한다")
        @Test
        void throwsWhenOrderIdNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new PaymentModel(null, 1L, CardType.SAMSUNG, "1234-****-****-1451", new Money(50000)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("userId가 null이면 예외가 발생한다")
        @Test
        void throwsWhenUserIdNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new PaymentModel(1L, null, CardType.SAMSUNG, "1234-****-****-1451", new Money(50000)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("cardType이 null이면 예외가 발생한다")
        @Test
        void throwsWhenCardTypeNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new PaymentModel(1L, 1L, null, "1234-****-****-1451", new Money(50000)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("maskedCardNo가 blank이면 예외가 발생한다")
        @Test
        void throwsWhenCardNoBlank() {
            CoreException result = assertThrows(CoreException.class,
                () -> new PaymentModel(1L, 1L, CardType.SAMSUNG, "  ", new Money(50000)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("amount가 null이면 예외가 발생한다")
        @Test
        void throwsWhenAmountNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new PaymentModel(1L, 1L, CardType.SAMSUNG, "1234-****-****-1451", null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("상태 전이")
    @Nested
    class StatusTransition {

        private PaymentModel createPayment() {
            return new PaymentModel(1L, 1L, CardType.SAMSUNG, "1234-****-****-1451", new Money(50000));
        }

        @DisplayName("transactionKey를 설정할 수 있다")
        @Test
        void assignsTransactionKey() {
            PaymentModel payment = createPayment();
            payment.assignTransactionKey("20250816:TR:9577c5");
            assertThat(payment.transactionKey()).isEqualTo("20250816:TR:9577c5");
        }

        @DisplayName("PENDING → SUCCESS 전이가 가능하다")
        @Test
        void transitionsToSuccess() {
            PaymentModel payment = createPayment();
            payment.markSuccess();
            assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("PENDING → FAILED 전이가 가능하다")
        @Test
        void transitionsToFailed() {
            PaymentModel payment = createPayment();
            payment.markFailed("한도 초과");
            assertAll(
                () -> assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(payment.failureReason()).isEqualTo("한도 초과")
            );
        }

        @DisplayName("SUCCESS 상태에서 다시 SUCCESS로 전이해도 멱등하다")
        @Test
        void idempotentSuccess() {
            PaymentModel payment = createPayment();
            payment.markSuccess();
            payment.markSuccess();
            assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("SUCCESS 상태에서 FAILED로 전이하면 예외가 발생한다")
        @Test
        void throwsWhenSuccessToFailed() {
            PaymentModel payment = createPayment();
            payment.markSuccess();
            assertThrows(CoreException.class, () -> payment.markFailed("오류"));
        }

        @DisplayName("FAILED 상태에서 SUCCESS로 전이하면 예외가 발생한다")
        @Test
        void throwsWhenFailedToSuccess() {
            PaymentModel payment = createPayment();
            payment.markFailed("한도 초과");
            assertThrows(CoreException.class, () -> payment.markSuccess());
        }
    }
}
