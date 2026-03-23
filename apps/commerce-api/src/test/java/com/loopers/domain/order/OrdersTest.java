package com.loopers.domain.order;

import com.loopers.domain.order.model.OrderProduct;
import com.loopers.domain.order.model.Orders;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrdersTest {

    @DisplayName("Orders를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 회원과 주문 상품 목록이 주어지면 정상 생성된다.")
        @Test
        void success() {
            // arrange
            Long memberId = 1L;
            OrderProduct orderProduct = OrderProduct.create(1L, "상품A", 10000, 2);

            // act
            Orders orders = assertDoesNotThrow(() -> Orders.create(memberId, List.of(orderProduct), 0, null));

            // assert
            assertAll(
                () -> assertThat(orders.getMemberId()).isEqualTo(1L),
                () -> assertThat(orders.getOrderProducts()).hasSize(1)
            );
        }

        @DisplayName("2개의 상품 합산 금액이 올바르게 계산된다.")
        @Test
        void calculatesCorrectTotalPrice() {
            // arrange
            Long memberId = 1L;
            OrderProduct product1 = OrderProduct.create(1L, "상품A", 10000, 2); // 10000 * 2 = 20000
            OrderProduct product2 = OrderProduct.create(2L, "상품B", 5000, 3);  // 5000 * 3 = 15000

            // act
            Orders orders = Orders.create(memberId, List.of(product1, product2), 0, null);

            // assert
            assertThat(orders.getTotalPrice().value()).isEqualTo(35000);
        }

        @DisplayName("주문자가 null이면 CoreException(BAD_REQUEST)이 발생한다.")
        @Test
        void throwsException_whenMemberIsNull() {
            // arrange
            OrderProduct orderProduct = OrderProduct.create(1L, "상품A", 10000, 2);

            // act & assert
            assertThatThrownBy(() -> Orders.create(null, List.of(orderProduct), 0, null))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("주문 상품 목록이 null이면 CoreException(BAD_REQUEST)이 발생한다.")
        @Test
        void throwsException_whenOrderProductsIsNull() {
            // arrange
            Long memberId = 1L;

            // act & assert
            assertThatThrownBy(() -> Orders.create(memberId, null, 0, null))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("주문 상품 목록이 비어 있으면 CoreException(BAD_REQUEST)이 발생한다.")
        @Test
        void throwsException_whenOrderProductsIsEmpty() {
            // arrange
            Long memberId = 1L;

            // act & assert
            assertThatThrownBy(() -> Orders.create(memberId, List.of(), 0, null))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @DisplayName("Orders를 복원할 때, ")
    @Nested
    class Reconstruct {

        @DisplayName("id와 totalPrice를 포함한 모든 필드가 올바르게 복원된다.")
        @Test
        void success() {
            // arrange
            Long memberId = 1L;
            OrderProduct orderProduct = OrderProduct.create(1L, "상품A", 10000, 2);

            // act
            Orders orders = assertDoesNotThrow(
                () -> Orders.reconstruct(1L, "ORD-001", memberId, 35000, 0, null, OrderStatus.CREATED, List.of(orderProduct))
            );

            // assert
            assertAll(
                () -> assertThat(orders.getId()).isEqualTo(1L),
                () -> assertThat(orders.getTotalPrice().value()).isEqualTo(35000)
            );
        }
    }

    @DisplayName("getOrderProducts()를 호출할 때, ")
    @Nested
    class GetOrderProducts {

        @DisplayName("반환된 목록에 원소를 추가하면 UnsupportedOperationException이 발생한다.")
        @Test
        void returnsUnmodifiableList() {
            // arrange
            Long memberId = 1L;
            OrderProduct orderProduct = OrderProduct.create(1L, "상품A", 10000, 2);
            Orders orders = Orders.create(memberId, List.of(orderProduct), 0, null);

            // act & assert
            assertThrows(
                UnsupportedOperationException.class,
                () -> orders.getOrderProducts().add(OrderProduct.create(2L, "상품B", 5000, 1))
            );
        }
    }
}
