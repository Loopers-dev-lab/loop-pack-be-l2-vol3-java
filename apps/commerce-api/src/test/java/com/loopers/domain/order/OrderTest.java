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

class OrderTest {

    @DisplayName("주문 생성 시, 상태는 ORDERED로 초기화되고 금액 필드가 계산된다.")
    @Test
    void setsStatusToOrdered_onCreate() {
        // arrange
        List<OrderItemSnapshot> snapshots = List.of(new OrderItemSnapshot(1L, "에어맥스", 150000L, 2));

        // act
        Order order = Order.create(1L, snapshots);

        // assert
        assertAll(
                () -> assertThat(order.getUserId()).isEqualTo(1L),
                () -> assertThat(order.getOriginalAmount()).isEqualTo(300000L),
                () -> assertThat(order.getDiscountAmount()).isZero(),
                () -> assertThat(order.getFinalAmount()).isEqualTo(300000L),
                () -> assertThat(order.getStatus()).isEqualTo(Order.Status.ORDERED)
        );
    }

    @DisplayName("결제 완료 처리 시, ")
    @Nested
    class MarkPaid {

        @DisplayName("ORDERED 상태에서 PAID로 전환된다.")
        @Test
        void transitionsToPaid_whenOrdered() {
            // arrange
            List<OrderItemSnapshot> snapshots = List.of(new OrderItemSnapshot(1L, "에어맥스", 150000L, 1));
            Order order = Order.create(1L, snapshots);

            // act
            order.markPaid();

            // assert
            assertThat(order.getStatus()).isEqualTo(Order.Status.PAID);
        }

        @DisplayName("ORDERED가 아닌 상태에서 호출하면 예외가 발생한다.")
        @Test
        void throwsException_whenNotOrdered() {
            // arrange
            List<OrderItemSnapshot> snapshots = List.of(new OrderItemSnapshot(1L, "에어맥스", 150000L, 1));
            Order order = Order.create(1L, snapshots);
            order.markPaid();

            // act
            CoreException result = assertThrows(CoreException.class, order::markPaid);

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("실패 처리 시, ")
    @Nested
    class MarkFailed {

        @DisplayName("ORDERED 상태에서 FAILED로 전환된다.")
        @Test
        void transitionsToFailed_whenOrdered() {
            // arrange
            List<OrderItemSnapshot> snapshots = List.of(new OrderItemSnapshot(1L, "에어맥스", 150000L, 1));
            Order order = Order.create(1L, snapshots);

            // act
            order.markFailed();

            // assert
            assertThat(order.getStatus()).isEqualTo(Order.Status.FAILED);
        }

        @DisplayName("ORDERED가 아닌 상태에서 호출하면 예외가 발생한다.")
        @Test
        void throwsException_whenNotOrdered() {
            // arrange
            List<OrderItemSnapshot> snapshots = List.of(new OrderItemSnapshot(1L, "에어맥스", 150000L, 1));
            Order order = Order.create(1L, snapshots);
            order.markFailed();

            // act
            CoreException result = assertThrows(CoreException.class, order::markFailed);

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
