package com.loopers.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.loopers.domain.shared.Money;
import com.loopers.support.BaseIntegrationTest;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class PaymentServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @DisplayName("결제를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 결제 정보이면, 결제가 DB에 저장된다.")
        @Test
        void savesPaymentToDatabase_whenValidPayment() {
            // arrange
            NewPayment newPayment = new NewPayment(
                    1L, 100L, "txn-key-123",
                    CardType.SHINHAN, "1234-5678-9012-3456", Money.wons(50000L)
            );

            // act
            Payment result = paymentService.create(newPayment);

            // assert
            assertAll(
                    () -> assertThat(result.getId()).isNotNull(),
                    () -> assertThat(result.getUserId()).isEqualTo(1L),
                    () -> assertThat(result.getOrderId()).isEqualTo(100L),
                    () -> assertThat(result.getTransactionKey()).isEqualTo("txn-key-123"),
                    () -> assertThat(result.getCardType()).isEqualTo(CardType.SHINHAN),
                    () -> assertThat(result.getCardNo()).isEqualTo("1234-5678-9012-3456"),
                    () -> assertThat(result.getAmount()).isEqualTo(Money.wons(50000L)),
                    () -> assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING)
            );
        }
    }

    @DisplayName("거래 키로 결제를 조회할 때,")
    @Nested
    class GetByTransactionKey {

        @DisplayName("존재하는 거래 키이면, 결제를 반환한다.")
        @Test
        void returnsPayment_whenTransactionKeyExists() {
            // arrange
            NewPayment newPayment = new NewPayment(
                    1L, 100L, "txn-find-test",
                    CardType.SHINHAN, "1234-5678-9012-3456", Money.wons(50000L)
            );
            paymentService.create(newPayment);

            // act
            Payment result = paymentService.getByTransactionKey("txn-find-test");

            // assert
            assertThat(result.getTransactionKey()).isEqualTo("txn-find-test");
        }

        @DisplayName("존재하지 않는 거래 키이면, PAYMENT_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenTransactionKeyNotFound() {
            assertThatThrownBy(() -> paymentService.getByTransactionKey("non-existent-key"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.PAYMENT_NOT_FOUND));
        }
    }
}
