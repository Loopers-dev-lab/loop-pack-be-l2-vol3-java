package com.loopers.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderItemTest {

    @DisplayName("getSubtotal은 상품 가격과 수량의 곱을 반환한다")
    @Test
    void getSubtotal_calculatesCorrectly() {
        OrderItem orderItem = new OrderItem(1L, "테스트 상품", 15000, "테스트 브랜드", 3);

        assertThat(orderItem.getSubtotal()).isEqualTo(45000);
    }
}
