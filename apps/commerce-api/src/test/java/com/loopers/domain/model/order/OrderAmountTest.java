package com.loopers.domain.model.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderAmountTest {

    @Test
    @DisplayName("of() - paymentAmount 자동 계산 (totalAmount - discountAmount)")
    void of_auto_calculate_paymentAmount() {
        OrderAmount amount = OrderAmount.of(PaymentMethod.CARD, Money.of(50000), Money.of(5000));

        assertThat(amount.getPaymentMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(amount.getTotalAmount().getValue()).isEqualTo(50000);
        assertThat(amount.getDiscountAmount().getValue()).isEqualTo(5000);
        assertThat(amount.getPaymentAmount().getValue()).isEqualTo(45000);
    }

    @Test
    @DisplayName("of() - discountAmount가 null이면 0원 처리")
    void of_null_discount_defaults_to_zero() {
        OrderAmount amount = OrderAmount.of(PaymentMethod.BANK_TRANSFER, Money.of(30000), null);

        assertThat(amount.getDiscountAmount().getValue()).isEqualTo(0);
        assertThat(amount.getPaymentAmount().getValue()).isEqualTo(30000);
    }

    @Test
    @DisplayName("of() - paymentMethod null이면 예외")
    void of_fail_null_paymentMethod() {
        assertThatThrownBy(() -> OrderAmount.of(null, Money.of(10000), Money.zero()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("결제 수단은 필수입니다");
    }

    @Test
    @DisplayName("of() - totalAmount null이면 예외")
    void of_fail_null_totalAmount() {
        assertThatThrownBy(() -> OrderAmount.of(PaymentMethod.CARD, null, Money.zero()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("총 금액은 필수입니다");
    }

    @Test
    @DisplayName("reconstitute() - 저장된 값 그대로 복원")
    void reconstitute_preserves_stored_values() {
        OrderAmount amount = OrderAmount.reconstitute(
                PaymentMethod.CARD, Money.of(50000), Money.of(5000), Money.of(45000));

        assertThat(amount.getPaymentMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(amount.getTotalAmount().getValue()).isEqualTo(50000);
        assertThat(amount.getDiscountAmount().getValue()).isEqualTo(5000);
        assertThat(amount.getPaymentAmount().getValue()).isEqualTo(45000);
    }
}
