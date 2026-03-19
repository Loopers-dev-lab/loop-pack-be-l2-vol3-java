package com.loopers.domain.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 결제 엔티티 단위 테스트 (06-payment-change-issues §1.1: PENDING일 때만 상태 전이).
 */
class PaymentModelTest {

    @Nested
    @DisplayName("createPending 시")
    class CreatePending {

        @Test
        @DisplayName("orderId가 null이면 IllegalArgumentException을 던진다.")
        void createPending_withNullOrderId_shouldThrowIAE() {
            // given / when
            assertThrows(IllegalArgumentException.class, () -> PaymentModel.createPending(null));
            // then
        }

        @Test
        @DisplayName("유효한 orderId면 PENDING 상태로 생성된다.")
        void createPending_withValidOrderId_shouldReturnPENDING() {
            // given
            PaymentModel p = PaymentModel.createPending(99L);
            // when
            // then
            assertThat(p.getOrderId()).isEqualTo(99L);
            assertThat(p.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(p.getPgTransactionId()).isNull();
            assertThat(p.isPending()).isTrue();
        }
    }

    @Nested
    @DisplayName("markSuccess 시")
    class MarkSuccess {

        @Test
        @DisplayName("PENDING이면 SUCCESS로 전이하고 pgTransactionId를 설정한다.")
        void markSuccess_whenPENDING_shouldSetSUCCESS() {
            // given
            PaymentModel p = PaymentModel.createPending(1L);
            // when
            p.markSuccess("tx-abc");
            // then
            assertThat(p.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(p.getPgTransactionId()).isEqualTo("tx-abc");
            assertThat(p.isPending()).isFalse();
        }

        @Test
        @DisplayName("PENDING이 아니면 IllegalStateException을 던진다.")
        void markSuccess_whenNotPENDING_shouldThrowISE() {
            // given
            PaymentModel p = PaymentModel.createPending(1L);
            p.markSuccess("tx-1");
            // when / then
            assertThrows(IllegalStateException.class, () -> p.markSuccess("tx-2"));
        }
    }

    @Nested
    @DisplayName("markFailed 시")
    class MarkFailed {

        @Test
        @DisplayName("PENDING이면 FAILED로 전이한다.")
        void markFailed_whenPENDING_shouldSetFAILED() {
            // given
            PaymentModel p = PaymentModel.createPending(1L);
            // when
            p.markFailed();
            // then
            assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @Test
        @DisplayName("PENDING이 아니면 IllegalStateException을 던진다.")
        void markFailed_whenNotPENDING_shouldThrowISE() {
            // given
            PaymentModel p = PaymentModel.createPending(1L);
            p.markSuccess("tx");
            // when / then
            assertThrows(IllegalStateException.class, p::markFailed);
        }
    }

    @Nested
    @DisplayName("markTimeout 시")
    class MarkTimeout {

        @Test
        @DisplayName("PENDING이면 TIMEOUT으로 전이한다.")
        void markTimeout_whenPENDING_shouldSetTIMEOUT() {
            // given
            PaymentModel p = PaymentModel.createPending(1L);
            // when
            p.markTimeout();
            // then
            assertThat(p.getStatus()).isEqualTo(PaymentStatus.TIMEOUT);
        }

        @Test
        @DisplayName("PENDING이 아니면 IllegalStateException을 던진다.")
        void markTimeout_whenNotPENDING_shouldThrowISE() {
            // given
            PaymentModel p = PaymentModel.createPending(1L);
            p.markFailed();
            // when / then
            assertThrows(IllegalStateException.class, p::markTimeout);
        }
    }
}
