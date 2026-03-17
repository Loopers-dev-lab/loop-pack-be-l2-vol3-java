package com.loopers.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
}
