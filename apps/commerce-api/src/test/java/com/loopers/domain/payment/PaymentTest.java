package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
                    () -> assertThat(payment.getPgTransactionId()).isNull(),
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
            assertThat(payment.getPgTransactionId()).isEqualTo("20250317:TR:abc123");
        }
    }

    @DisplayName("결제 완료 처리 시, ")
    @Nested
    class Complete {

        @DisplayName("PENDING 상태에서 COMPLETED로 전환된다.")
        @Test
        void transitionsToCompleted_whenPending() {
            // arrange
            Payment payment = Payment.create(1L, "pgOrderCode-uuid", CardType.HYUNDAI, "1234-5678-9012-3456", 20000L);

            // act
            payment.complete();

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @DisplayName("PENDING이 아닌 상태에서 호출하면 예외가 발생한다.")
        @Test
        void throwsException_whenNotPending() {
            // arrange
            Payment payment = Payment.create(1L, "pgOrderCode-uuid", CardType.SAMSUNG, "1234-5678-9012-3456", 10000L);
            payment.complete();

            // act
            CoreException result = assertThrows(CoreException.class, payment::complete);

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
        }
    }

    @DisplayName("결제 실패 처리 시, ")
    @Nested
    class Fail {

        @DisplayName("PENDING 상태에서 FAILED로 전환되고 실패 사유가 저장된다.")
        @Test
        void transitionsToFailed_whenPending() {
            // arrange
            Payment payment = Payment.create(1L, "pgOrderCode-uuid", CardType.SAMSUNG, "1234-5678-9012-3456", 10000L);

            // act
            payment.fail("한도초과입니다. 다른 카드를 선택해주세요.");

            // assert
            assertAll(
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                    () -> assertThat(payment.getFailReason()).isEqualTo("한도초과입니다. 다른 카드를 선택해주세요.")
            );
        }

        @DisplayName("PENDING이 아닌 상태에서 호출하면 예외가 발생한다.")
        @Test
        void throwsException_whenNotPending() {
            // arrange
            Payment payment = Payment.create(1L, "pgOrderCode-uuid", CardType.SAMSUNG, "1234-5678-9012-3456", 10000L);
            payment.fail("한도초과");

            // act
            CoreException result = assertThrows(CoreException.class, () -> payment.fail("잘못된 카드"));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
        }
    }
}
