package com.loopers.domain.order;

import com.loopers.domain.order.exception.InvalidOrderStatusTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @DisplayName("create: 주문 생성 시 PENDING 상태와 총액이 계산된다")
    @Test
    void create_success() {
        OrderItem orderItem1 = createSnapshotItem(1L, "상품A", BigDecimal.valueOf(1000), 2);
        OrderItem orderItem2 = createSnapshotItem(2L, "상품B", BigDecimal.valueOf(3000), 1);

        Order order = Order.create(10L, List.of(orderItem1, orderItem2));

        assertThat(order.getUserId()).isEqualTo(10L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getOrderItems()).hasSize(2);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("5000");
    }

    @DisplayName("create: 주문 항목이 없으면 예외가 발생한다")
    @Test
    void create_fail_emptyItems() {
        assertThatThrownBy(() -> Order.create(10L, List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("주문 항목은 1개 이상이어야 합니다");
    }

    @DisplayName("changeStatus: 상태를 변경할 수 있다")
    @Test
    void changeStatus_success() {
        Order order = Order.create(10L, List.of(createSnapshotItem(1L, "상품A", BigDecimal.valueOf(1000), 1)));

        order.changeStatus(OrderStatus.CONFIRMED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @DisplayName("cancel: 주문을 취소 상태로 변경한다")
    @Test
    void cancel_success() {
        Order order = Order.create(10L, List.of(createSnapshotItem(1L, "상품A", BigDecimal.valueOf(1000), 1)));

        boolean cancelled = order.cancel();

        assertThat(cancelled).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @DisplayName("changeStatus: 취소된 주문을 CONFIRMED로 변경하면 예외가 발생한다")
    @Test
    void changeStatus_fail_whenCancelledToConfirmed() {
        Order order = Order.create(10L, List.of(createSnapshotItem(1L, "상품A", BigDecimal.valueOf(1000), 1)));
        order.cancel();

        assertThatThrownBy(() -> order.changeStatus(OrderStatus.CONFIRMED))
            .isInstanceOf(InvalidOrderStatusTransitionException.class);
    }

    @DisplayName("cancel: 이미 취소된 주문을 다시 취소하면 false를 반환한다")
    @Test
    void cancel_idempotent_whenAlreadyCancelled() {
        Order order = Order.create(10L, List.of(createSnapshotItem(1L, "상품A", BigDecimal.valueOf(1000), 1)));
        order.cancel();

        boolean cancelled = order.cancel();

        assertThat(cancelled).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    private OrderItem createSnapshotItem(Long productId, String productName, BigDecimal price, Integer quantity) {
        return OrderItem.createSnapshot(productId, productName, price, quantity);
    }
}
