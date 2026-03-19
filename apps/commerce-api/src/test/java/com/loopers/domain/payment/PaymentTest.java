package com.loopers.domain.payment;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Payment 도메인 테스트")
class PaymentTest {

    @Nested
    @DisplayName("생성 테스트")
    class CreateTest {

        @Test
        @DisplayName("결제를 생성하면 PENDING 상태가 된다")
        void createPaymentWithPendingStatus() {
            Payment payment = Payment.create(1L, 100L, "VISA", "4111111111111111", Money.of(50000L));

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getOrderId()).isEqualTo(1L);
            assertThat(payment.getUserId()).isEqualTo(100L);
            assertThat(payment.getCardType()).isEqualTo("VISA");
            assertThat(payment.getCardNo()).isEqualTo("4111111111111111");
            assertThat(payment.getAmount()).isEqualTo(Money.of(50000L));
            assertThat(payment.getTransactionId()).isNull();
            assertThat(payment.getPgResponseMessage()).isNull();
        }

        @Test
        @DisplayName("주문 ID가 null이면 예외가 발생한다")
        void createWithNullOrderIdThrowsException() {
            assertThatThrownBy(() -> Payment.create(null, 100L, "VISA", "4111", Money.of(1000L)))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("주문 ID는 필수");
        }

        @Test
        @DisplayName("사용자 ID가 null이면 예외가 발생한다")
        void createWithNullUserIdThrowsException() {
            assertThatThrownBy(() -> Payment.create(1L, null, "VISA", "4111", Money.of(1000L)))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("사용자 ID는 필수");
        }

        @Test
        @DisplayName("카드 유형이 비어있으면 예외가 발생한다")
        void createWithBlankCardTypeThrowsException() {
            assertThatThrownBy(() -> Payment.create(1L, 100L, "", "4111", Money.of(1000L)))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("카드 유형은 필수");
        }

        @Test
        @DisplayName("카드 번호가 비어있으면 예외가 발생한다")
        void createWithBlankCardNoThrowsException() {
            assertThatThrownBy(() -> Payment.create(1L, 100L, "VISA", " ", Money.of(1000L)))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("카드 번호는 필수");
        }

        @Test
        @DisplayName("결제 금액이 null이면 예외가 발생한다")
        void createWithNullAmountThrowsException() {
            assertThatThrownBy(() -> Payment.create(1L, 100L, "VISA", "4111", null))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("결제 금액은 필수");
        }
    }

    @Nested
    @DisplayName("결제 완료 (complete) 테스트")
    class CompleteTest {

        @Test
        @DisplayName("PENDING 상태에서 complete하면 SUCCESS가 된다")
        void completeFromPending() {
            Payment payment = Payment.create(1L, 100L, "VISA", "4111", Money.of(50000L));

            payment.complete("txn-123", "결제 성공");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.getTransactionId()).isEqualTo("txn-123");
            assertThat(payment.getPgResponseMessage()).isEqualTo("결제 성공");
        }

        @Test
        @DisplayName("이미 SUCCESS 상태에서 complete를 호출하면 무시된다 (멱등)")
        void completeFromSuccessIsIdempotent() {
            Payment payment = Payment.create(1L, 100L, "VISA", "4111", Money.of(50000L));
            payment.complete("txn-123", "결제 성공");

            payment.complete("txn-456", "중복 콜백");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.getTransactionId()).isEqualTo("txn-123");
        }

        @Test
        @DisplayName("FAIL 상태에서 complete를 호출하면 예외가 발생한다")
        void completeFromFailThrowsException() {
            Payment payment = Payment.create(1L, 100L, "VISA", "4111", Money.of(50000L));
            payment.fail("한도 초과");

            assertThatThrownBy(() -> payment.complete("txn-123", "결제 성공"))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("PENDING 상태에서만 결제 완료");
        }
    }

    @Nested
    @DisplayName("결제 실패 (fail) 테스트")
    class FailTest {

        @Test
        @DisplayName("PENDING 상태에서 fail하면 FAIL이 된다")
        void failFromPending() {
            Payment payment = Payment.create(1L, 100L, "VISA", "4111", Money.of(50000L));

            payment.fail("카드 한도 초과");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAIL);
            assertThat(payment.getPgResponseMessage()).isEqualTo("카드 한도 초과");
        }

        @Test
        @DisplayName("이미 FAIL 상태에서 fail을 호출하면 무시된다 (멱등)")
        void failFromFailIsIdempotent() {
            Payment payment = Payment.create(1L, 100L, "VISA", "4111", Money.of(50000L));
            payment.fail("카드 한도 초과");

            payment.fail("중복 콜백");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAIL);
            assertThat(payment.getPgResponseMessage()).isEqualTo("카드 한도 초과");
        }

        @Test
        @DisplayName("SUCCESS 상태에서 fail을 호출하면 예외가 발생한다")
        void failFromSuccessThrowsException() {
            Payment payment = Payment.create(1L, 100L, "VISA", "4111", Money.of(50000L));
            payment.complete("txn-123", "결제 성공");

            assertThatThrownBy(() -> payment.fail("실패 처리"))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("PENDING 상태에서만 결제 실패");
        }
    }
}
