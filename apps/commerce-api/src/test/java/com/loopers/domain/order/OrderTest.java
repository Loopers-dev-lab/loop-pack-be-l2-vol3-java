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

    private static final OrderItem SAMPLE_ITEM = new OrderItem(
            1L, 2, "강아지 사료", 10000, "퍼피박스"
    );

    @Nested
    @DisplayName("주문 생성")
    class Create {

        @Test
        @DisplayName("유효한 항목으로 주문 생성 시 ORDERED 상태로 생성된다")
        void createOrderSuccess() {
            Order order = new Order(1L, "ORDER-001", List.of(SAMPLE_ITEM));

            assertThat(order.status()).isEqualTo(OrderStatus.ORDERED);
            assertThat(order.userId()).isEqualTo(1L);
            assertThat(order.items()).hasSize(1);
            assertThat(order.totalAmount()).isEqualTo(20000);
        }

        @Test
        @DisplayName("userId가 null이면 예외가 발생한다")
        void nullUserIdFails() {
            assertThatThrownBy(() -> new Order(null, "ORDER-001", List.of(SAMPLE_ITEM)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("빈 items로 주문 생성 시 예외가 발생한다")
        void emptyItemsFails() {
            assertThatThrownBy(() -> new Order(1L, "ORDER-001", List.of()))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @Nested
    @DisplayName("주문 취소")
    class Cancel {

        @Test
        @DisplayName("ORDERED 상태 주문을 취소하면 CANCELLED 상태가 된다")
        void cancelOrderedOrder() {
            Order order = new Order(1L, "ORDER-001", List.of(SAMPLE_ITEM));

            Order cancelled = order.cancel();

            assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("이미 취소된 주문을 재취소하면 409 예외가 발생한다")
        void cancelAlreadyCancelledOrderFails() {
            Order order = new Order(1L, "ORDER-001", List.of(SAMPLE_ITEM));
            Order cancelled = order.cancel();

            assertThatThrownBy(cancelled::cancel)
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }
    }

    @Nested
    @DisplayName("주문 소유자 확인")
    class Ownership {

        @Test
        @DisplayName("주문한 userId와 일치하면 true를 반환한다")
        void isOwnerReturnsTrue() {
            Order order = new Order(1L, "ORDER-001", List.of(SAMPLE_ITEM));

            assertThat(order.isOwner(1L)).isTrue();
        }

        @Test
        @DisplayName("주문한 userId와 다르면 false를 반환한다")
        void isOwnerReturnsFalse() {
            Order order = new Order(1L, "ORDER-001", List.of(SAMPLE_ITEM));

            assertThat(order.isOwner(2L)).isFalse();
        }
    }
}
