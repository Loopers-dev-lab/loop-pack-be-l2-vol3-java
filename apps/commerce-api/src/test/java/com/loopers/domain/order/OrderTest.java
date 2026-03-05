package com.loopers.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class OrderTest {

    @DisplayName("주문 생성 시, 상태는 ORDERED로 초기화되고 금액 필드가 계산된다.")
    @Test
    void setsStatusToOrdered_onCreate() {
        // arrange
        List<OrderItemSnapshot> snapshots = List.of(new OrderItemSnapshot(1L, "에어맥스", 150000L, 2));

        // act
        Order order = Order.create(1L, snapshots);

        // assert
        assertAll(
                () -> assertThat(order.getUserId()).isEqualTo(1L),
                () -> assertThat(order.getOriginalAmount()).isEqualTo(300000L),
                () -> assertThat(order.getDiscountAmount()).isZero(),
                () -> assertThat(order.getFinalAmount()).isEqualTo(300000L),
                () -> assertThat(order.getStatus()).isEqualTo(Order.Status.ORDERED)
        );
    }
}
