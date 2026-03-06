package com.loopers.domain.order;

import com.loopers.domain.order.model.OrderProduct;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class OrderProductTest {

    @DisplayName("OrderProduct를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 값이면 정상 생성된다.")
        @Test
        void success() {
            // arrange
            Long productId = 1L;
            String productName = "상품A";
            int price = 10000;
            int quantity = 2;

            // act
            OrderProduct orderProduct = assertDoesNotThrow(() -> OrderProduct.create(productId, productName, price, quantity));

            // assert
            assertAll(
                () -> assertThat(orderProduct.getProductId()).isEqualTo(1L),
                () -> assertThat(orderProduct.getProductName().value()).isEqualTo("상품A"),
                () -> assertThat(orderProduct.getPrice().value()).isEqualTo(10000),
                () -> assertThat(orderProduct.getQuantity().value()).isEqualTo(2)
            );
        }

        @DisplayName("상품명이 null이면 CoreException이 발생한다.")
        @Test
        void throwsException_whenNameIsNull() {
            // arrange & act & assert
            assertThatThrownBy(() -> OrderProduct.create(1L, null, 10000, 2))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("가격이 0이면 정상 생성된다.")
        @Test
        void success_whenPriceIsZero() {
            // arrange & act
            OrderProduct orderProduct = OrderProduct.create(1L, "상품A", 0, 2);

            // assert
            assertThat(orderProduct.getPrice().value()).isEqualTo(0);
        }

        @DisplayName("수량이 0이면 CoreException이 발생한다.")
        @Test
        void throwsException_whenQuantityIsZero() {
            // arrange & act & assert
            assertThatThrownBy(() -> OrderProduct.create(1L, "상품A", 10000, 0))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @DisplayName("OrderProduct를 복원할 때, ")
    @Nested
    class Reconstruct {

        @DisplayName("id와 모든 필드가 올바르게 복원된다.")
        @Test
        void success() {
            // arrange
            Long id = 1L;
            Long productId = 1L;
            String productName = "상품A";
            int price = 10000;
            int quantity = 2;

            // act
            OrderProduct orderProduct = assertDoesNotThrow(
                () -> OrderProduct.reconstruct(id, productId, productName, price, quantity)
            );

            // assert
            assertAll(
                () -> assertThat(orderProduct.getId()).isEqualTo(1L),
                () -> assertThat(orderProduct.getProductId()).isEqualTo(1L),
                () -> assertThat(orderProduct.getProductName().value()).isEqualTo("상품A"),
                () -> assertThat(orderProduct.getPrice().value()).isEqualTo(10000),
                () -> assertThat(orderProduct.getQuantity().value()).isEqualTo(2)
            );
        }
    }
}
