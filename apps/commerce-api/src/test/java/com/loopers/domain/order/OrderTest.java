package com.loopers.domain.order;

import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTest {

    @DisplayName("Order를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 정보이면, Order가 생성된다.")
        @Test
        void createsOrder_whenValidInfo() {
            Order order = new Order(1L, new Money(50000));

            assertAll(
                () -> assertThat(order.getUserId()).isEqualTo(1L),
                () -> assertThat(order.getTotalPrice()).isEqualTo(new Money(50000)),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.ORDERED)
            );
        }

        @DisplayName("userId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new Order(null, new Money(50000)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("totalPrice가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTotalPriceIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new Order(1L, null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
