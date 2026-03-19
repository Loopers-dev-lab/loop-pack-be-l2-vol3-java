package com.loopers.domain.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class PaymentTest {

    @DisplayName("결제 생성 시, ")
    @Nested
    class Create {

        @DisplayName("상태는 PENDING으로 초기화되고 pgTransactionId는 null이다.")
        @Test
        void initializesWithPendingStatusAndNullPgTransactionId() {
            // arrange & act
            Payment payment = Payment.create(1L, "pgOrderCode-uuid", CardType.SAMSUNG, "1234-5678-9012-3456", 10000L);

            // assert
            assertAll(
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                    () -> assertThat(payment.getPgTransactionKey()).isNull(),
                    () -> assertThat(payment.getFailReason()).isNull()
            );
        }
    }

    @DisplayName("PG 트랜잭션 ID 할당 시, ")
    @Nested
    class AssignPgTransaction {

        @DisplayName("pgTransactionId가 정상적으로 저장된다.")
        @Test
        void savesPgTransactionId() {
            // arrange
            Payment payment = Payment.create(1L, "pgOrderCode-uuid", CardType.KB, "1234-5678-9012-3456", 5000L);

            // act
            payment.assignPgTransaction("20250317:TR:abc123");

            // assert
            assertThat(payment.getPgTransactionKey()).isEqualTo("20250317:TR:abc123");
        }
    }

}
