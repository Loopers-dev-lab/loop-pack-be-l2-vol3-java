package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderModelTest {

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 정보가 주어지면, 총 주문 금액이 계산된다.")
        @Test
        void createsOrder_whenValidInfoIsProvided() {
            // arrange
            OrderItemModel item1 = new OrderItemModel(1L, "에어맥스", 150000L, 2);
            OrderItemModel item2 = new OrderItemModel(2L, "에어포스", 120000L, 1);

            // act
            OrderModel order = new OrderModel(10L, List.of(item1, item2));

            // assert
            assertAll(
                () -> assertThat(order.getUserId()).isEqualTo(10L),
                () -> assertThat(order.getOrderItems()).hasSize(2),
                () -> assertThat(order.getTotalAmount()).isEqualTo(420000L),
                () -> assertThat(order.getOrderItems().get(0).getOrder()).isEqualTo(order),
                () -> assertThat(order.getOrderItems().get(1).getOrder()).isEqualTo(order)
            );
        }

        @DisplayName("사용자 ID가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenUserIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                OrderItemModel item = new OrderItemModel(1L, "에어맥스", 150000L, 1);
                new OrderModel(null, List.of(item));
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("주문 상품이 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenOrderItemsAreEmpty() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new OrderModel(10L, List.of());
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
