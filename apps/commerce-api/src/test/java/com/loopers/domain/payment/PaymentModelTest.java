package com.loopers.domain.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class PaymentModelTest {

    @DisplayName("결제 생성 시 REQUESTED 상태로 시작한다.")
    @Test
    void startsRequestedStatus() {
        PaymentModel payment = new PaymentModel(1L, 100L, 5000L, CardType.SAMSUNG, "1234-5678-1111-2222");

        assertAll(
            () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED),
            () -> assertThat(payment.isTerminal()).isFalse()
        );
    }

    @DisplayName("PG가 요청을 수락하면 PENDING으로 변경되고 결제키가 저장된다.")
    @Test
    void marksPendingWhenAccepted() {
        PaymentModel payment = new PaymentModel(1L, 100L, 5000L, CardType.SAMSUNG, "1234-5678-1111-2222");

        payment.markRequestAccepted("20260319:TR:abc123");

        assertAll(
            () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
            () -> assertThat(payment.getPgPaymentKey()).isEqualTo("20260319:TR:abc123")
        );
    }

    @DisplayName("성공 상태가 되면 terminal 상태가 된다.")
    @Test
    void becomesTerminalWhenSuccess() {
        PaymentModel payment = new PaymentModel(1L, 100L, 5000L, CardType.SAMSUNG, "1234-5678-1111-2222");

        payment.markSuccess("20260319:TR:abc123");

        assertAll(
            () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
            () -> assertThat(payment.isTerminal()).isTrue()
        );
    }
}
