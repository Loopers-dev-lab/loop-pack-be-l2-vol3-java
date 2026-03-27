package com.loopers.domain.order.event;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.support.enums.OrderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderCreatedEvent 단위 테스트")
class OrderCreatedEventTest {

    @Test
    @DisplayName("OrderModel과 OrderItemModel로부터 OrderCreatedEvent가 올바르게 생성된다")
    void from_WithOrderModelAndItems_ShouldCreateEvent() {
        OrderModel order = OrderModel.create(10L, OrderType.DIRECT, BigDecimal.valueOf(20000));

        OrderItemModel item = OrderItemModel.create(
                1L, 1, 10L, 100L, 2,
                "테스트상품", BigDecimal.valueOf(10000),
                "brand-1", "테스트브랜드", null,
                BigDecimal.valueOf(20000), BigDecimal.ZERO, BigDecimal.valueOf(20000)
        );

        OrderCreatedEvent event = OrderCreatedEvent.from(order, List.of(item));

        assertThat(event.orderId()).isEqualTo(order.getOrderId());
        assertThat(event.userId()).isEqualTo(10L);
        assertThat(event.orderType()).isEqualTo(OrderType.DIRECT);
        assertThat(event.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(20000));
        assertThat(event.expiresAt()).isEqualTo(order.getExpiresAt());
        assertThat(event.items()).hasSize(1);
        assertThat(event.items().get(0).productId()).isEqualTo(100L);
        assertThat(event.items().get(0).quantity()).isEqualTo(2);
        assertThat(event.items().get(0).finalAmount()).isEqualByComparingTo(BigDecimal.valueOf(20000));
    }

    @Test
    @DisplayName("items가 null이면 빈 리스트로 변환된다")
    void from_WithNullItems_ShouldCreateEmptyList() {
        OrderModel order = OrderModel.create(10L, OrderType.CART, BigDecimal.valueOf(10000));

        OrderCreatedEvent event = OrderCreatedEvent.from(order, null);

        assertThat(event.items()).isEmpty();
    }
}
