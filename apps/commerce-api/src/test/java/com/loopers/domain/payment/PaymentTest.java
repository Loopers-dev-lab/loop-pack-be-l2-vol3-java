package com.loopers.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.loopers.domain.shared.Money;

class PaymentTest {

    @DisplayName("결제를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("정상 입력이면, PENDING 상태의 결제가 생성된다.")
        @Test
        void createsPaymentWithPendingStatus() {
            // arrange
            NewPayment newPayment = new NewPayment(
                    1L, 100L, "txn-key-123",
                    CardType.SHINHAN, "1234-5678-9012-3456", Money.wons(50000L)
            );

            // act
            Payment payment = Payment.create(newPayment);

            // assert
            assertAll(
                    () -> assertThat(payment.getUserId()).isEqualTo(newPayment.userId()),
                    () -> assertThat(payment.getOrderId()).isEqualTo(newPayment.orderId()),
                    () -> assertThat(payment.getTransactionKey()).isEqualTo(newPayment.transactionKey()),
                    () -> assertThat(payment.getCardType()).isEqualTo(newPayment.cardType()),
                    () -> assertThat(payment.getCardNo()).isEqualTo(newPayment.cardNo()),
                    () -> assertThat(payment.getAmount()).isEqualTo(newPayment.amount()),
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                    () -> assertThat(payment.getReason()).isNull()
            );
        }
    }

    @DisplayName("결제 결과를 반영할 때,")
    @Nested
    class Update {

        @DisplayName("PENDING 상태이면, 상태와 사유가 변경된다.")
        @Test
        void updatesStatusAndReason_whenPending() {
            // arrange
            Payment payment = PaymentFixture.createPendingPayment();

            // act
            payment.update(PaymentStatus.SUCCESS, "결제 승인");

            // assert
            assertAll(
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                    () -> assertThat(payment.getReason()).isEqualTo("결제 승인")
            );
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
                    Arguments.of(PaymentStatus.PENDING, false),
                    Arguments.of(PaymentStatus.SUCCESS, true),
                    Arguments.of(PaymentStatus.FAILED, true)
            );
        }
    }
}
