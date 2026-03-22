package com.loopers.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class PaymentTest {

    @DisplayName("결제를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("정상 입력이면, READY 상태의 결제가 생성된다.")
        @Test
        void createsPaymentWithReadyStatus() {
            // arrange
            NewPayment newPayment = new NewPayment(
                    1L, 100L,
                    CardType.SHINHAN, "1234-5678-9012-3456", Money.wons(50000L)
            );

            // act
            Payment payment = Payment.create(newPayment);

            // assert
            assertAll(
                    () -> assertThat(payment.getUserId()).isEqualTo(newPayment.userId()),
                    () -> assertThat(payment.getOrderId()).isEqualTo(newPayment.orderId()),
                    () -> assertThat(payment.getTransactionKey()).isNull(),
                    () -> assertThat(payment.getCardType()).isEqualTo(newPayment.cardType()),
                    () -> assertThat(payment.getCardNo()).isEqualTo(newPayment.cardNo()),
                    () -> assertThat(payment.getAmount()).isEqualTo(newPayment.amount()),
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY),
                    () -> assertThat(payment.getReason()).isNull()
            );
        }
    }

    @DisplayName("결제를 시작할 때,")
    @Nested
    class StartPayment {

        @DisplayName("READY 상태이면, PENDING으로 전이되고 transactionKey가 할당된다.")
        @Test
        void transitionsToPending_whenReady() {
            // arrange
            Payment payment = PaymentFixture.createReadyPayment();

            // act
            payment.confirmPayment("txn-key-123");

            // assert
            assertAll(
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                    () -> assertThat(payment.getTransactionKey()).isEqualTo("txn-key-123")
            );
        }

        @DisplayName("READY 상태가 아니면, PAYMENT_NOT_READY 예외가 발생한다.")
        @Test
        void throwsException_whenNotReady() {
            // arrange
            Payment payment = PaymentFixture.createPendingPayment();

            // act & assert
            assertThatThrownBy(() -> payment.confirmPayment("txn-key-456"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.PAYMENT_NOT_READY));
        }
    }

    @DisplayName("결제를 성공 처리할 때,")
    @Nested
    class Success {

        @DisplayName("PENDING 상태이면, SUCCESS로 전이되고 사유가 저장된다.")
        @Test
        void transitionsToSuccess_whenPending() {
            // arrange
            Payment payment = PaymentFixture.createPendingPayment();

            // act
            payment.success("결제 승인");

            // assert
            assertAll(
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                    () -> assertThat(payment.getReason()).isEqualTo("결제 승인")
            );
        }

        @DisplayName("이미 처리된 결제이면, PAYMENT_ALREADY_PROCESSED 예외가 발생한다.")
        @Test
        void throwsException_whenAlreadyProcessed() {
            // arrange
            Payment payment = PaymentFixture.createPendingPayment();
            payment.success("결제 승인");

            // act & assert
            assertThatThrownBy(() -> payment.success("재처리"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.PAYMENT_ALREADY_PROCESSED));
        }
    }

    @DisplayName("결제를 실패 처리할 때,")
    @Nested
    class Fail {

        @DisplayName("PENDING 상태이면, FAILED로 전이되고 사유가 저장된다.")
        @Test
        void transitionsToFailed_whenPending() {
            // arrange
            Payment payment = PaymentFixture.createPendingPayment();

            // act
            payment.fail("잔액 부족");

            // assert
            assertAll(
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                    () -> assertThat(payment.getReason()).isEqualTo("잔액 부족")
            );
        }

        @DisplayName("이미 처리된 결제이면, PAYMENT_ALREADY_PROCESSED 예외가 발생한다.")
        @Test
        void throwsException_whenAlreadyProcessed() {
            // arrange
            Payment payment = PaymentFixture.createPendingPayment();
            payment.fail("잔액 부족");

            // act & assert
            assertThatThrownBy(() -> payment.fail("재처리"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.PAYMENT_ALREADY_PROCESSED));
        }
    }

    @DisplayName("결제 처리 완료 여부를 확인할 때,")
    @Nested
    class IsProcessed {

        @DisplayName("{0} 상태이면, {1}를 반환한다.")
        @ParameterizedTest(name = "{0} 상태이면, {1}를 반환한다.")
        @MethodSource("statusAndExpected")
        void returnsExpectedResult(PaymentStatus status, boolean expected) {
            // arrange
            Payment payment = PaymentFixture.createPayment(status);

            // act & assert
            assertThat(payment.isProcessed()).isEqualTo(expected);
        }

        static Stream<Arguments> statusAndExpected() {
            return Stream.of(
                    Arguments.of(PaymentStatus.READY, false),
                    Arguments.of(PaymentStatus.PENDING, false),
                    Arguments.of(PaymentStatus.SUCCESS, true),
                    Arguments.of(PaymentStatus.FAILED, true)
            );
        }
    }
}
