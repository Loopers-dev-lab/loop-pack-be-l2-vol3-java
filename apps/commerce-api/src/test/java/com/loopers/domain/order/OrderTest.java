package com.loopers.domain.order;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTest {

    @DisplayName("Order를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 정보로 생성하면, 정상적으로 생성된다.")
        @Test
        void createsOrder_whenInfoIsValid() {
            // arrange
            Long userId = 1L;
            List<OrderItem> items = List.of(
                OrderItem.create(100L, "상품1", 2, new Money(10000)),
                OrderItem.create(200L, "상품2", 1, new Money(20000))
            );

            // act
            Order order = Order.create(userId, items);

            // assert
            assertThat(order.getUserId()).isEqualTo(userId);
            assertThat(order.getItems()).hasSize(2);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(order.getCreatedAt()).isNotNull();
        }

        @DisplayName("ID는 null로 생성된다.")
        @Test
        void createsWithNullId() {
            // arrange
            List<OrderItem> items = List.of(
                OrderItem.create(100L, "상품", 1, new Money(10000))
            );

            // act
            Order order = Order.create(1L, items);

            // assert
            assertThat(order.getId()).isNull();
        }

        @DisplayName("총액이 올바르게 계산된다.")
        @Test
        void calculatesTotalPriceCorrectly() {
            // arrange
            List<OrderItem> items = List.of(
                OrderItem.create(100L, "상품1", 2, new Money(10000)),  // 20000
                OrderItem.create(200L, "상품2", 1, new Money(20000)),  // 20000
                OrderItem.create(300L, "상품3", 3, new Money(5000))    // 15000
            );

            // act
            Order order = Order.create(1L, items);

            // assert
            assertThat(order.getTotalPrice().amount()).isEqualTo(55000);
        }

        @DisplayName("주문 항목이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenItemsIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                Order.create(1L, null);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("주문 항목이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenItemsIsEmpty() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                Order.create(1L, Collections.emptyList());
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("주문 항목이 불변 리스트로 보호된다.")
        @Test
        void itemsAreImmutable() {
            // arrange
            List<OrderItem> items = List.of(
                OrderItem.create(100L, "상품", 1, new Money(10000))
            );
            Order order = Order.create(1L, items);

            // act & assert
            assertThrows(UnsupportedOperationException.class, () -> {
                order.getItems().add(OrderItem.create(200L, "추가상품", 1, new Money(20000)));
            });
        }
    }

    @DisplayName("Order를 복원할 때,")
    @Nested
    class Reconstitute {

        @DisplayName("모든 필드가 복원된다.")
        @Test
        void reconstitutesAllFields() {
            // arrange
            Long id = 1L;
            Long userId = 10L;
            List<OrderItem> items = List.of(
                OrderItem.reconstitute(1L, 100L, "상품", 2, new Money(10000))
            );
            Money totalPrice = new Money(20000);
            OrderStatus status = OrderStatus.PAID;
            ZonedDateTime createdAt = ZonedDateTime.now().minusDays(1);

            // act
            Order order = Order.reconstitute(id, userId, items, totalPrice, status, createdAt);

            // assert
            assertThat(order.getId()).isEqualTo(id);
            assertThat(order.getUserId()).isEqualTo(userId);
            assertThat(order.getItems()).hasSize(1);
            assertThat(order.getTotalPrice()).isEqualTo(totalPrice);
            assertThat(order.getStatus()).isEqualTo(status);
            assertThat(order.getCreatedAt()).isEqualTo(createdAt);
        }
    }
}
