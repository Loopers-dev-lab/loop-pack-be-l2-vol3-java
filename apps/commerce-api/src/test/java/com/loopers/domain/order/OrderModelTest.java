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

class OrderModelTest {

    @DisplayName("주문 생성")
    @Nested
    class Create {

        @DisplayName("유효한 정보로 생성할 수 있다")
        @Test
        void createsWithValidInput() {
            // given
            Long userId = 1L;
            Money totalAmount = new Money(258000);

            // when
            OrderModel order = new OrderModel(userId, totalAmount);

            // then
            assertAll(
                () -> assertThat(order.userId()).isEqualTo(userId),
                () -> assertThat(order.status()).isEqualTo(OrderStatus.CREATED),
                () -> assertThat(order.totalAmount()).isEqualTo(totalAmount)
            );
        }

        @DisplayName("생성 시 상태는 CREATED이다")
        @Test
        void defaultStatusIsCreated() {
            // given & when
            OrderModel order = new OrderModel(1L, new Money(10000));

            // then
            assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
        }

        @DisplayName("userId가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsWhenUserIdNull() {
            // given & when
            CoreException result = assertThrows(CoreException.class,
                () -> new OrderModel(null, new Money(10000)));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("totalAmount가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsWhenTotalAmountNull() {
            // given & when
            CoreException result = assertThrows(CoreException.class,
                () -> new OrderModel(1L, null));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
