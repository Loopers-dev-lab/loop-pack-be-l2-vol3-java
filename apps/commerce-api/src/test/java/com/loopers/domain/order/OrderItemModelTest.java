package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderItemModelTest {

    @DisplayName("주문 항목을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("모든 필수 필드가 주어지면, 정상적으로 생성되고 lineTotalAmount가 계산된다.")
        @Test
        void createsOrderItem_whenAllRequiredFieldsAreProvided() {
            // act
            OrderItemModel orderItem = new OrderItemModel(1L, "에어맥스", 150000L, 2);

            // assert
            assertAll(
                () -> assertThat(orderItem.getProductId()).isEqualTo(1L),
                () -> assertThat(orderItem.getProductName()).isEqualTo("에어맥스"),
                () -> assertThat(orderItem.getUnitPrice()).isEqualTo(150000L),
                () -> assertThat(orderItem.getQuantity()).isEqualTo(2),
                () -> assertThat(orderItem.getLineTotalAmount()).isEqualTo(300000L)
            );
        }

        @DisplayName("productId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenProductIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new OrderItemModel(null, "에어맥스", 150000L, 2);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productName이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenProductNameIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new OrderItemModel(1L, "   ", 150000L, 2);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("unitPrice가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenUnitPriceIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new OrderItemModel(1L, "에어맥스", null, 2);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("unitPrice가 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenUnitPriceIsNegative() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new OrderItemModel(1L, "에어맥스", -1L, 2);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("quantity가 0 이하이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenQuantityIsZeroOrNegative() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new OrderItemModel(1L, "에어맥스", 150000L, 0);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
