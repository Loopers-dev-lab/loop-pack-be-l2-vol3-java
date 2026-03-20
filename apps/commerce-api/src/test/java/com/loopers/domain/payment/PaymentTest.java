package com.loopers.domain.payment;

import com.loopers.domain.payment.gateway.PgType;
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
        void 유효한_값이면_결제가_REQUESTED_상태로_생성된다() {
            Payment payment = Payment.create(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

            assertAll(
                    () -> assertThat(payment.getOrderId()).isEqualTo(1L),
                    () -> assertThat(payment.getUserId()).isEqualTo(100L),
                    () -> assertThat(payment.getCardType()).isEqualTo(CardType.SAMSUNG),
                    () -> assertThat(payment.getCardNo()).isEqualTo("1234-5678-9012-3456"),
                    () -> assertThat(payment.getAmount()).isEqualByComparingTo(new BigDecimal("50000")),
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED),
                    () -> assertThat(payment.getPaymentKey()).isNotNull(),
                    () -> assertThat(payment.getFailReason()).isNull()
            );
        }

        @Test
        void 주문ID가_null이면_예외() {
            assertThatThrownBy(() -> Payment.create(null, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000")))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        void 결제금액이_0이하이면_예외() {
            assertThatThrownBy(() -> Payment.create(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", BigDecimal.ZERO))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @Nested
    class 상태전이_SUCCEEDED {

        @Test
        void REQUESTED에서_SUCCEEDED로_변경된다() {
            Payment payment = Payment.create(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

            payment.markSucceeded();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        }

        @Test
        void FAILED_상태에서_SUCCEEDED로_변경하면_예외() {
            Payment payment = Payment.create(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));
            payment.markFailed("한도초과");

            assertThatThrownBy(() -> payment.markSucceeded())
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @Nested
    class 상태전이_FAILED {

        @Test
        void REQUESTED에서_FAILED로_변경된다() {
            Payment payment = Payment.create(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

            payment.markFailed("PG 요청 실패");

            assertAll(
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                    () -> assertThat(payment.getFailReason()).isEqualTo("PG 요청 실패")
            );
        }

        @Test
        void SUCCEEDED_상태에서_FAILED로_변경하면_예외() {
            Payment payment = Payment.create(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));
            payment.markSucceeded();

            assertThatThrownBy(() -> payment.markFailed("취소"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @Nested
    class 상태전이_CANCEL_REQUESTED {

        @Test
        void SUCCEEDED에서_CANCEL_REQUESTED로_변경된다() {
            Payment payment = Payment.create(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));
            payment.markSucceeded();

            payment.markCancelRequested("단순 변심");

            assertAll(
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED),
                    () -> assertThat(payment.getCancelReason()).isEqualTo("단순 변심")
            );
        }

        @Test
        void REQUESTED_상태에서_취소요청하면_예외() {
            Payment payment = Payment.create(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

            assertThatThrownBy(() -> payment.markCancelRequested("변심"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        void FAILED_상태에서_취소요청하면_예외() {
            Payment payment = Payment.create(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));
            payment.markFailed("실패");

            assertThatThrownBy(() -> payment.markCancelRequested("변심"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @Nested
    class 상태전이_CANCELED {

        @Test
        void CANCEL_REQUESTED에서_CANCELED로_변경된다() {
            Payment payment = Payment.create(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));
            payment.markSucceeded();
            payment.markCancelRequested("단순 변심");

            payment.markCanceled();

            assertAll(
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED),
                    () -> assertThat(payment.getCancelReason()).isEqualTo("단순 변심"),
                    () -> assertThat(payment.getCanceledAt()).isNotNull()
            );
        }

        @Test
        void SUCCEEDED_상태에서_취소확정하면_예외() {
            Payment payment = Payment.create(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));
            payment.markSucceeded();

            assertThatThrownBy(() -> payment.markCanceled())
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @Nested
    class 확정_여부 {

        @Test
        void SUCCEEDED이면_확정이다() {
            Payment payment = Payment.create(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));
            payment.markSucceeded();

            assertThat(payment.isFinalized()).isTrue();
        }

        @Test
        void FAILED이면_확정이다() {
            Payment payment = Payment.create(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));
            payment.markFailed("실패");

            assertThat(payment.isFinalized()).isTrue();
        }

        @Test
        void CANCELED이면_확정이다() {
            Payment payment = Payment.create(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));
            payment.markSucceeded();
            payment.markCancelRequested("변심");
            payment.markCanceled();

            assertThat(payment.isFinalized()).isTrue();
        }

        @Test
        void CANCEL_REQUESTED이면_미확정이다() {
            Payment payment = Payment.create(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));
            payment.markSucceeded();
            payment.markCancelRequested("변심");

            assertThat(payment.isFinalized()).isFalse();
        }

        @Test
        void REQUESTED이면_미확정이다() {
            Payment payment = Payment.create(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

            assertThat(payment.isFinalized()).isFalse();
        }
    }

    @Nested
    class 소유권_확인 {

        @Test
        void 본인의_결제이면_true를_반환한다() {
            Payment payment = Payment.create(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

            assertThat(payment.isOwnedBy(100L)).isTrue();
        }

        @Test
        void 본인의_결제가_아니면_false를_반환한다() {
            Payment payment = Payment.create(1L, 100L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

            assertThat(payment.isOwnedBy(999L)).isFalse();
        }
    }
}
