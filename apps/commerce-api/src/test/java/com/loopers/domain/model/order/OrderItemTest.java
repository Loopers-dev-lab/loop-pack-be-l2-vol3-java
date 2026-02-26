package com.loopers.domain.model.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderItemTest {

    @Test
    @DisplayName("주문 항목 생성 성공")
    void create_success() {
        OrderItem item = OrderItem.create(1L, 2, Money.of(10000));

        assertThat(item.getId()).isNull();
        assertThat(item.getProductId()).isEqualTo(1L);
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.getUnitPrice().getValue()).isEqualTo(10000);
    }

    @Test
    @DisplayName("productId null이면 예외")
    void create_fail_null_productId() {
        assertThatThrownBy(() -> OrderItem.create(null, 2, Money.of(10000)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("상품 ID는 필수입니다.");
    }

    @Test
    @DisplayName("수량 0 이하면 예외")
    void create_fail_zero_quantity() {
        assertThatThrownBy(() -> OrderItem.create(1L, 0, Money.of(10000)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 이상");
    }

    @Test
    @DisplayName("단가 null이면 예외")
    void create_fail_null_unitPrice() {
        assertThatThrownBy(() -> OrderItem.create(1L, 2, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("금액 계산 (단가 * 수량)")
    void calculateAmount() {
        OrderItem item = OrderItem.create(1L, 3, Money.of(10000));
        assertThat(item.calculateAmount().getValue()).isEqualTo(30000);
    }
}
