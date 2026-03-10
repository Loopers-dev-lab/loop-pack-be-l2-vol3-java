package com.loopers.application.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderItemValidatorTest {

    @DisplayName("주문 항목 검증 시, ")
    @Nested
    class Validate {

        @DisplayName("항목이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsAreEmpty() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                OrderItemValidator.validate(List.of());
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 0이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsZero() {
            // arrange
            List<OrderItemCommand> items = List.of(new OrderItemCommand(1L, 0));

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                OrderItemValidator.validate(items);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNegative() {
            // arrange
            List<OrderItemCommand> items = List.of(new OrderItemCommand(1L, -1));

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                OrderItemValidator.validate(items);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("중복 상품이 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDuplicateProducts() {
            // arrange
            List<OrderItemCommand> items = List.of(
                    new OrderItemCommand(1L, 1),
                    new OrderItemCommand(1L, 2)
            );

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                OrderItemValidator.validate(items);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
