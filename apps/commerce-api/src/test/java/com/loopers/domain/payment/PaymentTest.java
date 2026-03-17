package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PaymentTest {

    @Nested
    class 생성 {

        @Test
        void 유효한_값이면_결제가_PENDING_상태로_생성된다() {
            Payment payment = Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

            assertAll(
                    () -> assertThat(payment.getOrderId()).isEqualTo(1L),
                    () -> assertThat(payment.getUserId()).isEqualTo(100L),
                    () -> assertThat(payment.getCardType()).isEqualTo(CardType.SAMSUNG),
                    () -> assertThat(payment.getCardNo()).isEqualTo("1234-5678-9012-3456"),
                    () -> assertThat(payment.getAmount()).isEqualByComparingTo(new BigDecimal("50000")),
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                    () -> assertThat(payment.getTransactionKey()).isNull(),
                    () -> assertThat(payment.getFailReason()).isNull()
            );
        }

        @Test
        void 주문ID가_null이면_예외() {
            assertThatThrownBy(() -> Payment.create(null, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000")))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        void 결제금액이_0이하이면_예외() {
            assertThatThrownBy(() -> Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", BigDecimal.ZERO))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @Nested
    class 상태전이_IN_PROGRESS {

        @Test
        void PENDING에서_IN_PROGRESS로_변경된다() {
            Payment payment = Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

            payment.markInProgress("20250317:TR:abc123");

            assertAll(
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS),
                    () -> assertThat(payment.getTransactionKey()).isEqualTo("20250317:TR:abc123")
            );
        }

        @Test
        void IN_PROGRESS_상태에서_markInProgress_호출하면_예외() {
            Payment payment = Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));
            payment.markInProgress("20250317:TR:abc123");

            assertThatThrownBy(() -> payment.markInProgress("20250317:TR:def456"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @Nested
    class 상태전이_SUCCEEDED {

        @Test
        void IN_PROGRESS에서_SUCCEEDED로_변경된다() {
            Payment payment = Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));
            payment.markInProgress("20250317:TR:abc123");

            payment.markSucceeded("20250317:TR:abc123");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        }

        @Test
        void PENDING에서_SUCCEEDED로_변경된다() {
            Payment payment = Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

            payment.markSucceeded("20250317:TR:abc123");

            assertAll(
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED),
                    () -> assertThat(payment.getTransactionKey()).isEqualTo("20250317:TR:abc123")
            );
        }

        @Test
        void FAILED_상태에서_SUCCEEDED로_변경하면_예외() {
            Payment payment = Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));
            payment.markFailed("한도초과");

            assertThatThrownBy(() -> payment.markSucceeded("20250317:TR:abc123"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @Nested
    class 상태전이_FAILED {

        @Test
        void PENDING에서_FAILED로_변경된다() {
            Payment payment = Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

            payment.markFailed("PG 요청 실패");

            assertAll(
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                    () -> assertThat(payment.getFailReason()).isEqualTo("PG 요청 실패")
            );
        }

        @Test
        void IN_PROGRESS에서_FAILED로_변경된다() {
            Payment payment = Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));
            payment.markInProgress("20250317:TR:abc123");

            payment.markFailed("한도초과");

            assertAll(
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                    () -> assertThat(payment.getFailReason()).isEqualTo("한도초과")
            );
        }

        @Test
        void SUCCEEDED_상태에서_FAILED로_변경하면_예외() {
            Payment payment = Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));
            payment.markSucceeded("20250317:TR:abc123");

            assertThatThrownBy(() -> payment.markFailed("취소"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @Nested
    class 확정_여부 {

        @Test
        void SUCCEEDED이면_확정이다() {
            Payment payment = Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));
            payment.markSucceeded("20250317:TR:abc123");

            assertThat(payment.isFinalized()).isTrue();
        }

        @Test
        void FAILED이면_확정이다() {
            Payment payment = Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));
            payment.markFailed("실패");

            assertThat(payment.isFinalized()).isTrue();
        }

        @Test
        void PENDING이면_미확정이다() {
            Payment payment = Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

            assertThat(payment.isFinalized()).isFalse();
        }

        @Test
        void IN_PROGRESS이면_미확정이다() {
            Payment payment = Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));
            payment.markInProgress("20250317:TR:abc123");

            assertThat(payment.isFinalized()).isFalse();
        }
    }

    @Nested
    class 소유권_확인 {

        @Test
        void 본인의_결제이면_true를_반환한다() {
            Payment payment = Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

            assertThat(payment.isOwnedBy(100L)).isTrue();
        }

        @Test
        void 본인의_결제가_아니면_false를_반환한다() {
            Payment payment = Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

            assertThat(payment.isOwnedBy(999L)).isFalse();
        }
    }
}
