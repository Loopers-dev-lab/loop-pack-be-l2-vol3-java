package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderItemTest {

    @DisplayName("주문 항목 생성 시, ")
    @Nested
    class Create {

        @DisplayName("수량이 1 이상이면 정상적으로 생성된다.")
        @Test
        void createsOrderItem_whenQuantityIsAtLeastOne() {
            // act
            OrderItem item = OrderItem.create(1L, 10L, "에어맥스", 150000, 1);

            // assert
            assertThat(item.getQuantity()).isEqualTo(1);
        }

        @DisplayName("수량이 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                OrderItem.create(1L, 10L, "에어맥스", 150000, null);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 0이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsZero() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                OrderItem.create(1L, 10L, "에어맥스", 150000, 0);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNegative() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                OrderItem.create(1L, 10L, "에어맥스", 150000, -1);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
