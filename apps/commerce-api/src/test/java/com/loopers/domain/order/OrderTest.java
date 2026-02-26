package com.loopers.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class OrderTest {

    @DisplayName("주문 생성 시, 상태는 ORDERED로 초기화된다.")
    @Test
    void setsStatusToOrdered_onCreate() {
        // act
        Order order = Order.create(1L, 300000L);

        // assert
        assertAll(
                () -> assertThat(order.getUserId()).isEqualTo(1L),
                () -> assertThat(order.getTotalAmount()).isEqualTo(300000L),
                () -> assertThat(order.getStatus()).isEqualTo(Order.Status.ORDERED)
        );
    }
}
