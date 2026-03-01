package com.loopers.domain.order;

import com.loopers.domain.order.exception.EmptyOrderItemException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderDomainServiceTest {

    private OrderDomainService orderDomainService;

    @BeforeEach
    void setUp() {
        orderDomainService = new OrderDomainService();
    }

    @DisplayName("createOrder: 주문 항목이 주어지면 PENDING 상태의 주문을 생성한다")
    @Test
    void createOrder_success() {
        OrderItem orderItem = OrderItem.createSnapshot(1L, "테스트 상품", BigDecimal.valueOf(2000), 3);

        Order order = orderDomainService.createOrder(10L, List.of(orderItem));

        assertThat(order.getUserId()).isEqualTo(10L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getOrderItems()).hasSize(1);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("6000");
    }

    @DisplayName("createOrder: 주문 항목이 비어있으면 예외가 발생한다")
    @Test
    void createOrder_fail_emptyItems() {
        assertThatThrownBy(() -> orderDomainService.createOrder(10L, List.of()))
            .isInstanceOf(EmptyOrderItemException.class);
    }

    @DisplayName("createOrder: 여러 항목이 있으면 totalAmount가 합산된다")
    @Test
    void createOrder_totalAmount_summed() {
        OrderItem item1 = OrderItem.createSnapshot(1L, "상품A", BigDecimal.valueOf(1000), 2);
        OrderItem item2 = OrderItem.createSnapshot(2L, "상품B", BigDecimal.valueOf(3000), 1);

        Order order = orderDomainService.createOrder(10L, List.of(item1, item2));

        assertThat(order.getTotalAmount()).isEqualByComparingTo("5000");
    }

    @DisplayName("cancelOrder: PENDING 주문을 취소하면 CANCELLED 상태가 되고 true를 반환한다")
    @Test
    void cancelOrder_success() {
        OrderItem orderItem = OrderItem.createSnapshot(1L, "테스트 상품", BigDecimal.valueOf(1000), 2);
        Order order = orderDomainService.createOrder(10L, List.of(orderItem));

        boolean cancelled = orderDomainService.cancelOrder(order);

        assertThat(cancelled).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @DisplayName("cancelOrder: 이미 취소된 주문이면 false를 반환하고 상태는 변하지 않는다")
    @Test
    void cancelOrder_idempotent_whenAlreadyCancelled() {
        OrderItem orderItem = OrderItem.createSnapshot(1L, "테스트 상품", BigDecimal.valueOf(1000), 1);
        Order order = orderDomainService.createOrder(10L, List.of(orderItem));
        order.cancel();

        boolean cancelled = orderDomainService.cancelOrder(order);

        assertThat(cancelled).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }
}
