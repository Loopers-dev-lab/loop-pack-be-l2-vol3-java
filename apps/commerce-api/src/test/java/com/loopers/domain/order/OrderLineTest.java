package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderLineTest {

    @DisplayName("OrderLine을 생성할 때")
    @Nested
    class Create {

        @DisplayName("유효한 값이 주어지면 성공한다")
        @Test
        void success() {
            OrderLine orderLine = new OrderLine(1L, 2, 10_000L);

            assertThat(orderLine.getTotalPrice()).isEqualTo(20_000L);
        }

        @DisplayName("상품ID가 null이면 예외가 발생한다")
        @Test
        void failsWhenProductIdIsNull() {
            assertThatThrownBy(() -> new OrderLine(null, 1, 10_000L))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 0 이하면 예외가 발생한다")
        @Test
        void failsWhenQuantityIsZeroOrNegative() {
            assertThatThrownBy(() -> new OrderLine(1L, 0, 10_000L))
                .isInstanceOf(CoreException.class);
        }
    }
}
