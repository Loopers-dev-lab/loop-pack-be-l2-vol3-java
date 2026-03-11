package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.PaymentErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PaymentTest {

    @DisplayName("생성할 때,")
    @Nested
    class 생성 {

        @Test
        void 유효한_정보면_REQUESTED_상태로_생성된다() {
            // act
            Payment payment = Payment.request(1L, 50000, "CARD", "IDEM-001");

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
        }

        @Test
        void requestedAt이_설정된다() {
            // act
            Payment payment = Payment.request(1L, 50000, "CARD", "IDEM-001");

            // assert
            assertThat(payment.getRequestedAt()).isNotNull();
        }

        @Test
        void paymentMethod가_설정된다() {
            // act
            Payment payment = Payment.request(1L, 50000, "CARD", "IDEM-001");

            // assert
            assertThat(payment.getPaymentMethod()).isEqualTo("CARD");
        }

        @Test
        void idempotencyKey가_설정된다() {
            // act
            Payment payment = Payment.request(1L, 50000, "CARD", "IDEM-001");

            // assert
            assertThat(payment.getIdempotencyKey()).isEqualTo("IDEM-001");
        }
    }

    @DisplayName("승인할 때,")
    @Nested
    class 승인 {

        @Test
        void 승인_시_APPROVED로_전이된다() {
            // arrange
            Payment payment = Payment.request(1L, 50000, "CARD", "IDEM-001");

            // act
            payment.approve("PG-TXN-001", 50000);

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        }

        @Test
        void pgTxnId와_approvedAmount가_설정된다() {
            // arrange
            Payment payment = Payment.request(1L, 50000, "CARD", "IDEM-001");

            // act
            payment.approve("PG-TXN-001", 50000);

            // assert
            assertThat(payment)
                    .extracting(Payment::getPgTxnId, Payment::getApprovedAmount)
                    .containsExactly("PG-TXN-001", 50000);
        }
    }

    @DisplayName("실패할 때,")
    @Nested
    class 실패 {

        @Test
        void 실패_시_FAILED로_전이된다() {
            // arrange
            Payment payment = Payment.request(1L, 50000, "CARD", "IDEM-001");

            // act
            payment.reject();

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @Test
        void failedAt이_설정된다() {
            // arrange
            Payment payment = Payment.request(1L, 50000, "CARD", "IDEM-001");

            // act
            payment.reject();

            // assert
            assertThat(payment.getFailedAt()).isNotNull();
        }
    }

    @DisplayName("취소할 때,")
    @Nested
    class 취소 {

        @Test
        void APPROVED가_아니면_예외가_발생한다() {
            // arrange
            Payment payment = Payment.request(1L, 50000, "CARD", "IDEM-001");

            // act & assert
            assertThatThrownBy(payment::cancel)
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(PaymentErrorType.INVALID_PAYMENT_STATUS);
        }

        @Test
        void APPROVED이면_CANCELED로_전이된다() {
            // arrange
            Payment payment = Payment.request(1L, 50000, "CARD", "IDEM-001");
            payment.approve("PG-TXN-001", 50000);

            // act
            payment.cancel();

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        }
    }
}
