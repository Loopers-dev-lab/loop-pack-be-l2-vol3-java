package com.loopers.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Nested
    @DisplayName("Order 생성")
    class Create {

        @DisplayName("주문 항목들의 소계 합산으로 totalPrice가 계산된다")
        @Test
        void create_withItems_calculatesTotalPrice() {
            OrderItem item1 = new OrderItem(1L, "상품A", 10000, "브랜드A", 2);
            OrderItem item2 = new OrderItem(2L, "상품B", 5000, "브랜드B", 3);

            Order order = Order.create(1L, List.of(item1, item2));

            assertThat(order.getMemberId()).isEqualTo(1L);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(order.getTotalPrice()).isEqualTo(35000);
            assertThat(order.getItems()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("주문 취소")
    class Cancel {

        @DisplayName("주문을 취소하면 상태가 CANCELLED로 변경된다")
        @Test
        void cancel_changesStatusToCancelled() {
            OrderItem item = new OrderItem(1L, "상품A", 10000, "브랜드A", 1);
            Order order = Order.create(1L, List.of(item));

            order.cancel();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("주문 항목 조회")
    class GetItems {

        @DisplayName("getItems는 수정 불가능한 리스트를 반환한다")
        @Test
        void getItems_returnsUnmodifiableList() {
            OrderItem item = new OrderItem(1L, "상품A", 10000, "브랜드A", 1);
            Order order = Order.create(1L, List.of(item));

            List<OrderItem> items = order.getItems();

            assertThatThrownBy(() -> items.add(new OrderItem(2L, "상품B", 5000, "브랜드B", 1)))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
