package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PaymentModel Entity")
class PaymentModelTest {

    private static final Long ORDER_ID = 1L;
    private static final Long MEMBER_ID = 1L;
    private static final CardType CARD_TYPE = CardType.SAMSUNG;
    private static final String CARD_NUMBER = "1234-5678-9012-3456";
    private static final BigDecimal AMOUNT = new BigDecimal("10000");

    @DisplayName("결제를 생성할 때,")
    @Nested
    class Create {

        @Test
        @DisplayName("정상 입력으로 생성 시 PENDING 상태로 생성된다")
        void create_withValidInput_returnsPendingPayment() {
            PaymentModel payment = PaymentModel.create(ORDER_ID, MEMBER_ID, CARD_TYPE, CARD_NUMBER, AMOUNT);

            assertThat(payment.getRefOrderId().value()).isEqualTo(ORDER_ID);
            assertThat(payment.getRefMemberId()).isEqualTo(MEMBER_ID);
            assertThat(payment.getCardType()).isEqualTo(CARD_TYPE);
            assertThat(payment.getCardNumber()).isEqualTo(CARD_NUMBER);
            assertThat(payment.getAmount()).isEqualByComparingTo(AMOUNT);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getPgTransactionId()).isNull();
        }

        @Test
        @DisplayName("카드 번호 형식이 xxxx-xxxx-xxxx-xxxx가 아니면 예외가 발생한다")
        void create_withInvalidCardNumberFormat_throwsException() {
            assertThatThrownBy(() -> PaymentModel.create(ORDER_ID, MEMBER_ID, CARD_TYPE, "1234567890123456", AMOUNT))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("카드 번호가 null이면 예외가 발생한다")
        void create_withNullCardNumber_throwsException() {
            assertThatThrownBy(() -> PaymentModel.create(ORDER_ID, MEMBER_ID, CARD_TYPE, null, AMOUNT))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("카드 종류가 null이면 예외가 발생한다")
        void create_withNullCardType_throwsException() {
            assertThatThrownBy(() -> PaymentModel.create(ORDER_ID, MEMBER_ID, null, CARD_NUMBER, AMOUNT))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("결제 금액이 0이면 예외가 발생한다")
        void create_withZeroAmount_throwsException() {
            assertThatThrownBy(() -> PaymentModel.create(ORDER_ID, MEMBER_ID, CARD_TYPE, CARD_NUMBER, BigDecimal.ZERO))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("결제 금액이 음수이면 예외가 발생한다")
        void create_withNegativeAmount_throwsException() {
            assertThatThrownBy(() -> PaymentModel.create(ORDER_ID, MEMBER_ID, CARD_TYPE, CARD_NUMBER, new BigDecimal("-1")))
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("결제 요청 상태로 전이할 때,")
    @Nested
    class Requested {

        @Test
        @DisplayName("PENDING 상태에서 requested() 호출 시 REQUESTED 상태가 된다")
        void requested_fromPending_success() {
            PaymentModel payment = PaymentModel.create(ORDER_ID, MEMBER_ID, CARD_TYPE, CARD_NUMBER, AMOUNT);
            String pgTransactionId = "20260315:TR:cb62da";

            payment.requested(pgTransactionId);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
            assertThat(payment.getPgTransactionId()).isEqualTo(pgTransactionId);
        }

        @Test
        @DisplayName("REQUESTED 상태에서 requested() 호출 시 예외가 발생한다")
        void requested_fromRequested_throwsException() {
            PaymentModel payment = PaymentModel.create(ORDER_ID, MEMBER_ID, CARD_TYPE, CARD_NUMBER, AMOUNT);
            payment.requested("20260315:TR:cb62da");

            assertThatThrownBy(() -> payment.requested("20260315:TR:aabbcc"))
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("결제 완료 상태로 전이할 때,")
    @Nested
    class Complete {

        @Test
        @DisplayName("REQUESTED 상태에서 complete() 호출 시 COMPLETED 상태가 된다")
        void complete_fromRequested_success() {
            PaymentModel payment = PaymentModel.create(ORDER_ID, MEMBER_ID, CARD_TYPE, CARD_NUMBER, AMOUNT);
            payment.requested("20260315:TR:cb62da");

            payment.complete();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("PENDING 상태에서 complete() 호출 시 예외가 발생한다")
        void complete_fromPending_throwsException() {
            PaymentModel payment = PaymentModel.create(ORDER_ID, MEMBER_ID, CARD_TYPE, CARD_NUMBER, AMOUNT);

            assertThatThrownBy(payment::complete)
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("COMPLETED 상태에서 complete() 호출 시 예외가 발생한다")
        void complete_fromCompleted_throwsException() {
            PaymentModel payment = PaymentModel.create(ORDER_ID, MEMBER_ID, CARD_TYPE, CARD_NUMBER, AMOUNT);
            payment.requested("20260315:TR:cb62da");
            payment.complete();

            assertThatThrownBy(payment::complete)
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("결제 실패 상태로 전이할 때,")
    @Nested
    class Fail {

        @Test
        @DisplayName("PENDING 상태에서 fail() 호출 시 FAILED 상태가 된다")
        void fail_fromPending_success() {
            PaymentModel payment = PaymentModel.create(ORDER_ID, MEMBER_ID, CARD_TYPE, CARD_NUMBER, AMOUNT);

            payment.fail();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @Test
        @DisplayName("REQUESTED 상태에서 fail() 호출 시 FAILED 상태가 된다")
        void fail_fromRequested_success() {
            PaymentModel payment = PaymentModel.create(ORDER_ID, MEMBER_ID, CARD_TYPE, CARD_NUMBER, AMOUNT);
            payment.requested("20260315:TR:cb62da");

            payment.fail();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @Test
        @DisplayName("COMPLETED 상태에서 fail() 호출 시 예외가 발생한다")
        void fail_fromCompleted_throwsException() {
            PaymentModel payment = PaymentModel.create(ORDER_ID, MEMBER_ID, CARD_TYPE, CARD_NUMBER, AMOUNT);
            payment.requested("20260315:TR:cb62da");
            payment.complete();

            assertThatThrownBy(payment::fail)
                    .isInstanceOf(CoreException.class);
        }
    }
}
