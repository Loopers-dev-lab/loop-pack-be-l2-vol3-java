package com.loopers.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PaymentTest {

    private static final Long DEFAULT_ORDER_ID = 1L;
    private static final String DEFAULT_PG_TRANSACTION_ID = "20250316:TR:abc123";

    private static void assertCoreException(Runnable runnable, String message) {
        assertThatThrownBy(runnable::run)
            .isInstanceOf(CoreException.class)
            .hasMessage(message);
    }

    @Nested
    @DisplayName("결제 생성")
    class Create {

        @Test
        @DisplayName("결제 생성에 성공한다")
        void success_create_payment() {
            Payment payment = Payment.create(DEFAULT_ORDER_ID);

            assertThat(payment.getRefOrderId()).isEqualTo(DEFAULT_ORDER_ID);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getPgTransactionId()).isNull();
            assertThat(payment.getFailReason()).isNull();
        }

        @DisplayName("주문FK가 유효하지 않으면, 생성시 예외를 던진다")
        @ParameterizedTest
        @NullSource
        @ValueSource(longs = {-1L, 0L})
        void fail_when_invalid_order_id(Long orderId) {
            assertCoreException(
                () -> Payment.create(orderId),
                ErrorMessage.Payment.ORDER_ID_INVALID
            );
        }
    }

    @Nested
    @DisplayName("결제 성공 처리")
    class MarkSuccess {

        @Test
        @DisplayName("PENDING 상태에서 성공 처리하면, 상태가 SUCCESS로 변경된다")
        void success_mark_success() {
            Payment payment = Payment.create(DEFAULT_ORDER_ID);

            payment.markSuccess(DEFAULT_PG_TRANSACTION_ID);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.getPgTransactionId()).isEqualTo(DEFAULT_PG_TRANSACTION_ID);
        }

        @Test
        @DisplayName("이미 처리된 결제를 성공 처리하면, 예외를 던진다")
        void fail_when_already_processed() {
            Payment payment = Payment.create(DEFAULT_ORDER_ID);
            payment.markSuccess(DEFAULT_PG_TRANSACTION_ID);

            assertCoreException(
                () -> payment.markSuccess("another-tx-id"),
                ErrorMessage.Payment.ALREADY_PROCESSED
            );
        }

        @Test
        @DisplayName("PG 거래 ID가 null이면, 예외를 던진다")
        void fail_when_pg_transaction_id_is_null() {
            Payment payment = Payment.create(DEFAULT_ORDER_ID);

            assertCoreException(
                () -> payment.markSuccess(null),
                ErrorMessage.Payment.PG_TRANSACTION_ID_REQUIRED
            );
        }

        @Test
        @DisplayName("PG 거래 ID가 비어있으면, 예외를 던진다")
        void fail_when_pg_transaction_id_is_blank() {
            Payment payment = Payment.create(DEFAULT_ORDER_ID);

            assertCoreException(
                () -> payment.markSuccess("   "),
                ErrorMessage.Payment.PG_TRANSACTION_ID_REQUIRED
            );
        }
    }

    @Nested
    @DisplayName("결제 실패 처리")
    class MarkFailed {

        @Test
        @DisplayName("PENDING 상태에서 실패 처리하면, 상태가 FAILED로 변경된다")
        void success_mark_failed() {
            Payment payment = Payment.create(DEFAULT_ORDER_ID);

            payment.markFailed("한도 초과");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getFailReason()).isEqualTo("한도 초과");
        }

        @Test
        @DisplayName("이미 처리된 결제를 실패 처리하면, 예외를 던진다")
        void fail_when_already_processed() {
            Payment payment = Payment.create(DEFAULT_ORDER_ID);
            payment.markFailed("한도 초과");

            assertCoreException(
                () -> payment.markFailed("다른 사유"),
                ErrorMessage.Payment.ALREADY_PROCESSED
            );
        }
    }
}
