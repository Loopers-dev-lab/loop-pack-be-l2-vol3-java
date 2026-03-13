package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class OrderTest {

    @DisplayName("주문을 생성할 때")
    @Nested
    class Create {

        @DisplayName("유효한 회원ID와 주문 항목이 주어지면 성공한다")
        @Test
        void success() {
            Long memberId = 1L;
            List<OrderLine> orderLines = List.of(
                new OrderLine(1L, 2, 10_000L),
                new OrderLine(2L, 1, 5_000L)
            );

            Order order = Order.create(memberId, orderLines);

            assertAll(
                () -> assertThat(order.getMemberId()).isEqualTo(memberId),
                () -> assertThat(order.getStatus()).isEqualTo("ORDERED"),
                () -> assertThat(order.getOrderLines()).hasSize(2),
                () -> assertThat(order.getTotalAmount()).isEqualTo(25_000L)
            );
        }

        @DisplayName("회원ID가 null이면 예외가 발생한다")
        @Test
        void failsWhenMemberIdIsNull() {
            List<OrderLine> orderLines = List.of(new OrderLine(1L, 1, 10_000L));

            assertThatThrownBy(() -> Order.create(null, orderLines))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }

        @DisplayName("주문 항목이 비어있으면 예외가 발생한다")
        @Test
        void failsWhenOrderLinesIsEmpty() {
            assertThatThrownBy(() -> Order.create(1L, List.of()))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }
    }
}
