package com.loopers.application.order.event;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderPlacedEventTest {

    @DisplayName("Order로부터 OrderPlacedEvent를 생성하면 orderId, userId, items가 올바르게 매핑된다")
    @Test
    void from_mapsOrderFieldsCorrectly() {
        OrderItem item1 = OrderItem.createSnapshot(10L, "상품A", BigDecimal.valueOf(1000), 2);
        OrderItem item2 = OrderItem.createSnapshot(20L, "상품B", BigDecimal.valueOf(2000), 1);
        Order order = Order.create(100L, List.of(item1, item2));

        OrderPlacedEvent event = OrderPlacedEvent.from(order);

        assertThat(event.userId()).isEqualTo(100L);
        assertThat(event.items()).hasSize(2);
        assertThat(event.items().get(0).productId()).isEqualTo(10L);
        assertThat(event.items().get(0).quantity()).isEqualTo(2);
        assertThat(event.items().get(1).productId()).isEqualTo(20L);
        assertThat(event.items().get(1).quantity()).isEqualTo(1);
    }
}
