package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Payment 엔티티 단위 테스트")
class PaymentTest {

    @Nested
    @DisplayName("create - 결제 생성")
    class Create {

        @Test
        @DisplayName("성공: 유효한 정보로 결제를 생성한다")
        void create_success() {
            // Given
            Long orderId = 1L;
            Long userId = 1L;
            BigDecimal amount = new BigDecimal("50000");
            CardType cardType = CardType.SAMSUNG;
            String cardNo = "1234-5678-9012-3456";

            // When
            Payment payment = Payment.create(orderId, userId, amount, cardType, cardNo);

            // Then
            assertThat(payment.getOrderId()).isEqualTo(orderId);
            assertThat(payment.getUserId()).isEqualTo(userId);
            assertThat(payment.getAmount()).isEqualByComparingTo(amount);
            assertThat(payment.getCardType()).isEqualTo(CardType.SAMSUNG);
            assertThat(payment.getCardNo()).isEqualTo(cardNo);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
            assertThat(payment.getTransactionKey()).isNull();
            assertThat(payment.getFailureReason()).isNull();
        }

        @Test
        @DisplayName("실패: orderId가 null이면 BAD_REQUEST 예외를 던진다")
        void create_nullOrderId_fail() {
            assertThatThrownBy(() -> Payment.create(null, 1L, new BigDecimal("10000"), CardType.SAMSUNG, "1234"))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }

        @Test
        @DisplayName("실패: userId가 null이면 BAD_REQUEST 예외를 던진다")
        void create_nullUserId_fail() {
            assertThatThrownBy(() -> Payment.create(1L, null, new BigDecimal("10000"), CardType.SAMSUNG, "1234"))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }

        @Test
        @DisplayName("실패: amount가 null이면 BAD_REQUEST 예외를 던진다")
        void create_nullAmount_fail() {
            assertThatThrownBy(() -> Payment.create(1L, 1L, null, CardType.SAMSUNG, "1234"))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }

        @Test
        @DisplayName("실패: amount가 0 이하면 BAD_REQUEST 예외를 던진다")
        void create_zeroAmount_fail() {
            assertThatThrownBy(() -> Payment.create(1L, 1L, BigDecimal.ZERO, CardType.SAMSUNG, "1234"))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }

        @Test
        @DisplayName("실패: cardType이 null이면 BAD_REQUEST 예외를 던진다")
        void create_nullCardType_fail() {
            assertThatThrownBy(() -> Payment.create(1L, 1L, new BigDecimal("10000"), null, "1234"))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }

        @Test
        @DisplayName("실패: cardNo가 빈 값이면 BAD_REQUEST 예외를 던진다")
        void create_blankCardNo_fail() {
            assertThatThrownBy(() -> Payment.create(1L, 1L, new BigDecimal("10000"), CardType.SAMSUNG, ""))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("markPending - PG 호출 시작")
    class MarkPending {

        @Test
        @DisplayName("성공: REQUESTED 상태에서 PENDING으로 전이한다")
        void markPending_success() {
            // Given
            Payment payment = Payment.create(1L, 1L, new BigDecimal("10000"), CardType.KB, "1234");

            // When
            payment.markPending();

            // Then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        @DisplayName("실패: PENDING 상태에서는 다시 PENDING으로 전이할 수 없다")
        void markPending_fromPending_fail() {
            // Given
            Payment payment = Payment.create(1L, 1L, new BigDecimal("10000"), CardType.KB, "1234");
            payment.markPending();

            // When & Then
            assertThatThrownBy(payment::markPending)
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("markSuccess - 결제 성공")
    class MarkSuccess {

        @Test
        @DisplayName("성공: PENDING 상태에서 SUCCESS로 전이하고 transactionKey를 저장한다")
        void markSuccess_success() {
            // Given
            Payment payment = Payment.create(1L, 1L, new BigDecimal("10000"), CardType.HYUNDAI, "1234");
            payment.markPending();
            String transactionKey = "txn-12345";

            // When
            payment.markSuccess(transactionKey);

            // Then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.getTransactionKey()).isEqualTo(transactionKey);
        }

        @Test
        @DisplayName("실패: REQUESTED 상태에서는 SUCCESS로 전이할 수 없다")
        void markSuccess_fromRequested_fail() {
            // Given
            Payment payment = Payment.create(1L, 1L, new BigDecimal("10000"), CardType.KB, "1234");

            // When & Then
            assertThatThrownBy(() -> payment.markSuccess("txn-123"))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("markFailed - 결제 실패")
    class MarkFailed {

        @Test
        @DisplayName("성공: PENDING 상태에서 FAILED로 전이하고 실패 사유를 저장한다")
        void markFailed_success() {
            // Given
            Payment payment = Payment.create(1L, 1L, new BigDecimal("10000"), CardType.SAMSUNG, "1234");
            payment.markPending();
            String failureReason = "잔액 부족";

            // When
            payment.markFailed(failureReason);

            // Then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getFailureReason()).isEqualTo(failureReason);
        }

        @Test
        @DisplayName("실패: REQUESTED 상태에서는 FAILED로 전이할 수 없다")
        void markFailed_fromRequested_fail() {
            // Given
            Payment payment = Payment.create(1L, 1L, new BigDecimal("10000"), CardType.KB, "1234");

            // When & Then
            assertThatThrownBy(() -> payment.markFailed("에러"))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }
    }
}
