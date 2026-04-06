package com.loopers.domain.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.loopers.domain.shared.Money;

class OrderEventTest {

    @DisplayName("OrderItemSnapshot을 생성할 때,")
    @Nested
    class OrderItemSnapshotFrom {

        @DisplayName("OrderItem의 productId, quantity, price를 매핑한다.")
        @Test
        void mapsProductIdQuantityAndPrice() {
            // arrange
            var cartItem = new Cart.CartItem(1L, "상품A", "https://thumb.png", Money.wons(50000L), 2L);
            var orderItem = OrderItem.create(cartItem);

            // act
            List<OrderEvent.OrderItemSnapshot> snapshots = OrderEvent.OrderItemSnapshot.from(List.of(orderItem));

            // assert
            assertThat(snapshots).hasSize(1);
            OrderEvent.OrderItemSnapshot snapshot = snapshots.get(0);
            assertThat(snapshot.productId()).isEqualTo(1L);
            assertThat(snapshot.quantity()).isEqualTo(2L);
            assertThat(snapshot.price()).isEqualTo(50000L);
        }
    }
}
