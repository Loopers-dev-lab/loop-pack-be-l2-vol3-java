package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentTest {

    @DisplayName("Payment 를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 값이 주어지면, status 가 PENDING 으로 생성된다.")
        @Test
        void createsPayment_withPendingStatus() {
            // arrange
            String orderId = "20260318-ABCD12";
            Long userId = 1L;
            String cardType = "신한카드";
            String cardNo = "1234-5678-9012-3456";
            Long amount = 50000L;

            // act
            Payment payment = Payment.of(orderId, userId, cardType, cardNo, amount);

            // assert
            assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
        }

        @DisplayName("orderId 가 null 이면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenOrderIdIsNull() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                Payment.of(null, 1L, "신한카드", "1234-5678-9012-3456", 50000L)
            );

            // assert
            assertThat(result.getCustomMessage()).isEqualTo("주문번호는 비어있을 수 없습니다.");
        }

        @DisplayName("orderId 가 blank 이면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenOrderIdIsBlank() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                Payment.of("  ", 1L, "신한카드", "1234-5678-9012-3456", 50000L)
            );

            // assert
            assertThat(result.getCustomMessage()).isEqualTo("주문번호는 비어있을 수 없습니다.");
        }

        @DisplayName("userId 가 null 이면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenUserIdIsNull() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                Payment.of("20260318-ABCD12", null, "신한카드", "1234-5678-9012-3456", 50000L)
            );

            // assert
            assertThat(result.getCustomMessage()).isEqualTo("결제자ID는 비어있을 수 없습니다.");
        }

        @DisplayName("cardType 이 null 이면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenCardTypeIsNull() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                Payment.of("20260318-ABCD12", 1L, null, "1234-5678-9012-3456", 50000L)
            );

            // assert
            assertThat(result.getCustomMessage()).isEqualTo("카드타입은 비어있을 수 없습니다.");
        }

        @DisplayName("cardType 이 blank 이면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenCardTypeIsBlank() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                Payment.of("20260318-ABCD12", 1L, "  ", "1234-5678-9012-3456", 50000L)
            );

            // assert
            assertThat(result.getCustomMessage()).isEqualTo("카드타입은 비어있을 수 없습니다.");
        }

        @DisplayName("cardNo 가 null 이면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenCardNoIsNull() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                Payment.of("20260318-ABCD12", 1L, "신한카드", null, 50000L)
            );

            // assert
            assertThat(result.getCustomMessage()).isEqualTo("카드번호는 비어있을 수 없습니다.");
        }

        @DisplayName("cardNo 가 blank 이면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenCardNoIsBlank() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                Payment.of("20260318-ABCD12", 1L, "신한카드", "  ", 50000L)
            );

            // assert
            assertThat(result.getCustomMessage()).isEqualTo("카드번호는 비어있을 수 없습니다.");
        }

        @DisplayName("amount 가 null 이면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenAmountIsNull() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                Payment.of("20260318-ABCD12", 1L, "신한카드", "1234-5678-9012-3456", null)
            );

            // assert
            assertThat(result.getCustomMessage()).isEqualTo("결제금액은 양의 정수여야 합니다.");
        }

        @DisplayName("amount 가 0 이면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenAmountIsZero() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                Payment.of("20260318-ABCD12", 1L, "신한카드", "1234-5678-9012-3456", 0L)
            );

            // assert
            assertThat(result.getCustomMessage()).isEqualTo("결제금액은 양의 정수여야 합니다.");
        }

        @DisplayName("amount 가 음수이면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenAmountIsNegative() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                Payment.of("20260318-ABCD12", 1L, "신한카드", "1234-5678-9012-3456", -1L)
            );

            // assert
            assertThat(result.getCustomMessage()).isEqualTo("결제금액은 양의 정수여야 합니다.");
        }
    }

    @DisplayName("결제를 재시도할 때 (reset), ")
    @Nested
    class Reset {

        @DisplayName("FAILED 상태이면, 카드 정보가 업데이트되고 status 가 PENDING 으로 변경된다.")
        @Test
        void resetsToLending_andUpdatesCardInfo_whenStatusIsFailed() {
            // arrange
            Payment payment = Payment.of("20260318-ABCD12", 1L, "신한카드", "1234-5678-9012-3456", 50000L);
            payment.applyPgResult("tx-key-001", PaymentStatus.FAILED, "잔액 부족");

            // act
            payment.reset("국민카드", "9999-8888-7777-6666");

            // assert
            assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.cardType()).isEqualTo("국민카드");
            assertThat(payment.cardNo()).isEqualTo("9999-8888-7777-6666");
        }

        @DisplayName("SUCCESS 상태이면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenStatusIsSuccess() {
            // arrange
            Payment payment = Payment.of("20260318-ABCD12", 1L, "신한카드", "1234-5678-9012-3456", 50000L);
            payment.applyPgResult("tx-key-001", PaymentStatus.SUCCESS, null);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                payment.reset("국민카드", "9999-8888-7777-6666")
            );

            // assert
            assertThat(result.getCustomMessage()).isEqualTo("이미 완료된 결제입니다.");
        }

        @DisplayName("PENDING 상태이면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenStatusIsPending() {
            // arrange
            Payment payment = Payment.of("20260318-ABCD12", 1L, "신한카드", "1234-5678-9012-3456", 50000L);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                payment.reset("국민카드", "9999-8888-7777-6666")
            );

            // assert
            assertThat(result.getCustomMessage()).isEqualTo("이미 진행 중인 결제입니다.");
        }
    }

    @DisplayName("PG 결과를 반영할 때, ")
    @Nested
    class ApplyPgResult {

        @DisplayName("PENDING 상태이면, transactionKey, status, reason 이 반영된다.")
        @Test
        void appliesPgResult_whenStatusIsPending() {
            // arrange
            Payment payment = Payment.of("20260318-ABCD12", 1L, "신한카드", "1234-5678-9012-3456", 50000L);

            // act
            payment.applyPgResult("tx-key-001", PaymentStatus.SUCCESS, null);

            // assert
            assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("FAILED 상태에서 SUCCESS 가 오면, SUCCESS 로 전이된다.")
        @Test
        void transitionsToSuccess_whenFailedAndSuccessArrives() {
            // arrange
            Payment payment = Payment.of("20260318-ABCD12", 1L, "신한카드", "1234-5678-9012-3456", 50000L);
            payment.applyPgResult("tx-key-001", PaymentStatus.FAILED, "잔액 부족");

            // act
            payment.applyPgResult("tx-key-001", PaymentStatus.SUCCESS, null);

            // assert
            assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("FAILED 상태에서 FAILED 가 오면, 무시된다.")
        @Test
        void ignores_whenFailedAndFailedArrives() {
            // arrange
            Payment payment = Payment.of("20260318-ABCD12", 1L, "신한카드", "1234-5678-9012-3456", 50000L);
            payment.applyPgResult("tx-key-001", PaymentStatus.FAILED, "잔액 부족");

            // act
            payment.applyPgResult("tx-key-001", PaymentStatus.FAILED, "잔액 부족");

            // assert
            assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
        }

        @DisplayName("SUCCESS 상태에서 동일한 transactionKey 로 SUCCESS 가 오면, 무시된다.")
        @Test
        void ignores_whenSuccessAndSameTransactionKeyArrives() {
            // arrange
            Payment payment = Payment.of("20260318-ABCD12", 1L, "신한카드", "1234-5678-9012-3456", 50000L);
            payment.applyPgResult("tx-key-001", PaymentStatus.SUCCESS, null);

            // act & assert (예외 없이 통과)
            payment.applyPgResult("tx-key-001", PaymentStatus.SUCCESS, null);
            assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("SUCCESS 상태에서 다른 transactionKey 로 SUCCESS 가 오면, 중복 결제 예외가 발생한다.")
        @Test
        void throwsCoreException_whenSuccessAndDifferentTransactionKeyArrives() {
            // arrange
            Payment payment = Payment.of("20260318-ABCD12", 1L, "신한카드", "1234-5678-9012-3456", 50000L);
            payment.applyPgResult("tx-key-001", PaymentStatus.SUCCESS, null);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                payment.applyPgResult("tx-key-002", PaymentStatus.SUCCESS, null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(com.loopers.support.error.ErrorType.CONFLICT);
        }

        @DisplayName("SUCCESS 상태에서 FAILED 가 오면, 무시된다.")
        @Test
        void ignores_whenSuccessAndFailedArrives() {
            // arrange
            Payment payment = Payment.of("20260318-ABCD12", 1L, "신한카드", "1234-5678-9012-3456", 50000L);
            payment.applyPgResult("tx-key-001", PaymentStatus.SUCCESS, null);

            // act
            payment.applyPgResult("tx-key-001", PaymentStatus.FAILED, "오류");

            // assert
            assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCESS);
        }
    }
}
