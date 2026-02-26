package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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

        @DisplayName("주문 항목이 비어있으면 예외가 발생한다")
        @Test
        void create_withEmptyItems_throwsException() {
            assertThatThrownBy(() -> Order.create(1L, List.of()))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("주문 항목이 null이면 예외가 발생한다")
        @Test
        void create_withNullItems_throwsException() {
            assertThatThrownBy(() -> Order.create(1L, null))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("주문 항목들의 소계 합산으로 totalPrice가 계산된다")
        @Test
        void create_withItems_calculatesTotalPrice() {
            Order.ItemSnapshot snap1 = new Order.ItemSnapshot(1L, "상품A", 10000, "브랜드A", 2);
            Order.ItemSnapshot snap2 = new Order.ItemSnapshot(2L, "상품B", 5000, "브랜드B", 3);

            Order order = Order.create(1L, List.of(snap1, snap2));

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
            Order order = Order.create(1L, List.of(
                    new Order.ItemSnapshot(1L, "상품A", 10000, "브랜드A", 1)));

            order.cancel();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @DisplayName("이미 취소된 주문을 다시 취소하면 예외가 발생한다")
        @Test
        void cancel_whenAlreadyCancelled_throwsException() {
            Order order = Order.create(1L, List.of(
                    new Order.ItemSnapshot(1L, "상품A", 10000, "브랜드A", 1)));
            order.cancel();

            assertThatThrownBy(order::cancel)
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("주문 항목 조회")
    class GetItems {

        @DisplayName("getItems는 수정 불가능한 리스트를 반환한다")
        @Test
        void getItems_returnsUnmodifiableList() {
            Order order = Order.create(1L, List.of(
                    new Order.ItemSnapshot(1L, "상품A", 10000, "브랜드A", 1)));

            List<OrderItem> items = order.getItems();

            assertThatThrownBy(() -> items.add(new OrderItem(2L, "상품B", 5000, "브랜드B", 1)))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
