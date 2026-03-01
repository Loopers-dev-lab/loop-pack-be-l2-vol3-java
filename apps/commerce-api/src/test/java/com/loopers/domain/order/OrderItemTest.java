package com.loopers.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderItemTest {

    @DisplayName("createSnapshot: 상품 정보와 수량으로 스냅샷과 소계를 생성한다")
    @Test
    void createSnapshot_success() {
        OrderItem orderItem = OrderItem.createSnapshot(1L, "테스트 상품", BigDecimal.valueOf(15000), 2);

        assertThat(orderItem.getProductId()).isEqualTo(1L);
        assertThat(orderItem.getProductName()).isEqualTo("테스트 상품");
        assertThat(orderItem.getProductPrice()).isEqualByComparingTo("15000");
        assertThat(orderItem.getQuantity()).isEqualTo(2);
        assertThat(orderItem.getSubtotal()).isEqualByComparingTo("30000");
    }

    @DisplayName("createSnapshot: 수량이 0 이하이면 예외가 발생한다")
    @Test
    void createSnapshot_fail_invalidQuantity() {
        assertThatThrownBy(() -> OrderItem.createSnapshot(1L, "테스트 상품", BigDecimal.valueOf(15000), 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("주문 수량은 1 이상이어야 합니다");
    }

    @DisplayName("createSnapshot: 상품 ID가 null이면 예외가 발생한다")
    @Test
    void createSnapshot_fail_nullProductId() {
        assertThatThrownBy(() -> OrderItem.createSnapshot(null, "테스트 상품", BigDecimal.valueOf(15000), 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("상품 ID는 필수입니다");
    }

    @DisplayName("createSnapshot: 상품명이 blank이면 예외가 발생한다")
    @Test
    void createSnapshot_fail_blankProductName() {
        assertThatThrownBy(() -> OrderItem.createSnapshot(1L, "", BigDecimal.valueOf(15000), 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("상품명은 필수입니다");
    }
}
