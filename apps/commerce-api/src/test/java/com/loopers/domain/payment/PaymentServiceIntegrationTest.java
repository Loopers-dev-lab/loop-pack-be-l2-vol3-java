package com.loopers.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.loopers.domain.shared.Money;
import com.loopers.support.BaseIntegrationTest;

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
}
