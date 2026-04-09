package com.loopers.domain.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStatusHistoryTest {

    @DisplayName("create() — 모든 필드가 정확히 설정된다")
    @Test
    void create_allFieldsSet() {
        PaymentStatusHistory history = PaymentStatusHistory.create(
            1L, PaymentStatus.PENDING, PaymentStatus.PAID, "CALLBACK", null);

        assertThat(history.getPaymentId()).isEqualTo(1L);
        assertThat(history.getFromStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(history.getToStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(history.getReason()).isEqualTo("CALLBACK");
        assertThat(history.getDetail()).isNull();
    }

    @DisplayName("create() — detail 포함")
    @Test
    void create_withDetail() {
        PaymentStatusHistory history = PaymentStatusHistory.create(
            2L, PaymentStatus.PENDING, PaymentStatus.FAILED, "POLLING", "한도 초과");

        assertThat(history.getPaymentId()).isEqualTo(2L);
        assertThat(history.getFromStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(history.getToStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(history.getReason()).isEqualTo("POLLING");
        assertThat(history.getDetail()).isEqualTo("한도 초과");
    }
}
