package com.loopers.domain.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    @DisplayName("create: 유효한 값으로 Payment를 생성하면 PENDING 상태로 생성된다")
    @Test
    void create_success() {
        Payment payment = Payment.create(1L, 2L, "SAMSUNG", "1234-5678-9814-1451", BigDecimal.valueOf(5000));

        assertThat(payment.getOrderId()).isEqualTo(1L);
        assertThat(payment.getUserId()).isEqualTo(2L);
        assertThat(payment.getCardType()).isEqualTo("SAMSUNG");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getPgTransactionId()).isNull();
    }

    @DisplayName("create: 주문 금액이 0 이하이면 예외가 발생한다")
    @Test
    void create_fail_invalidAmount() {
        assertThatThrownBy(() -> Payment.create(1L, 2L, "SAMSUNG", "1234-5678", BigDecimal.ZERO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("결제 금액");
    }

    @DisplayName("markSuccess: PENDING 결제를 성공 처리하면 SUCCESS 상태가 된다")
    @Test
    void markSuccess_success() {
        Payment payment = Payment.create(1L, 2L, "SAMSUNG", "1234-5678", BigDecimal.valueOf(5000));

        payment.markSuccess("20250816:TR:abc123");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getPgTransactionId()).isEqualTo("20250816:TR:abc123");
    }

    @DisplayName("markSuccess: 이미 SUCCESS 상태에서 다시 호출하면 예외가 발생한다")
    @Test
    void markSuccess_fail_alreadyProcessed() {
        Payment payment = Payment.create(1L, 2L, "SAMSUNG", "1234-5678", BigDecimal.valueOf(5000));
        payment.markSuccess("txId1");

        assertThatThrownBy(() -> payment.markSuccess("txId2"))
            .isInstanceOf(IllegalStateException.class);
    }

    @DisplayName("markFailed: PENDING 결제를 실패 처리하면 FAILED 상태가 된다")
    @Test
    void markFailed_success() {
        Payment payment = Payment.create(1L, 2L, "SAMSUNG", "1234-5678", BigDecimal.valueOf(5000));

        payment.markFailed("20250816:TR:abc123");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @DisplayName("markFailed: pgTransactionId 없이 실패 처리해도 상태는 FAILED가 된다")
    @Test
    void markFailed_withoutTransactionId() {
        Payment payment = Payment.create(1L, 2L, "SAMSUNG", "1234-5678", BigDecimal.valueOf(5000));

        payment.markFailed(null);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getPgTransactionId()).isNull();
    }

    @DisplayName("assignTransactionId: PG 트랜잭션 ID를 저장한다")
    @Test
    void assignTransactionId_success() {
        Payment payment = Payment.create(1L, 2L, "SAMSUNG", "1234-5678", BigDecimal.valueOf(5000));

        payment.assignTransactionId("20250816:TR:abc123");

        assertThat(payment.getPgTransactionId()).isEqualTo("20250816:TR:abc123");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @DisplayName("markRefunded: SUCCESS 결제를 환불 처리하면 REFUNDED 상태가 된다")
    @Test
    void markRefunded_success() {
        Payment payment = Payment.create(1L, 2L, "SAMSUNG", "1234-5678", BigDecimal.valueOf(5000));
        payment.markSuccess("txId1");

        payment.markRefunded();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @DisplayName("markRefunded: PENDING 상태에서 환불 처리하면 예외가 발생한다")
    @Test
    void markRefunded_fail_fromPending() {
        Payment payment = Payment.create(1L, 2L, "SAMSUNG", "1234-5678", BigDecimal.valueOf(5000));

        assertThatThrownBy(payment::markRefunded)
            .isInstanceOf(IllegalStateException.class);
    }

    @DisplayName("markRefunded: FAILED 상태에서 환불 처리하면 예외가 발생한다")
    @Test
    void markRefunded_fail_fromFailed() {
        Payment payment = Payment.create(1L, 2L, "SAMSUNG", "1234-5678", BigDecimal.valueOf(5000));
        payment.markFailed("txId1");

        assertThatThrownBy(payment::markRefunded)
            .isInstanceOf(IllegalStateException.class);
    }
}
