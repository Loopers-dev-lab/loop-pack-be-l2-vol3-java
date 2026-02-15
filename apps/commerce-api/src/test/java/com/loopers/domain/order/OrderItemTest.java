package com.loopers.domain.order;

import com.loopers.domain.Quantity;
import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderItemTest {

    @DisplayName("OrderItem을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 정보이면, OrderItem이 생성된다.")
        @Test
        void createsOrderItem_whenValidInfo() {
            OrderItem orderItem = new OrderItem(1L, 100L, "에어맥스", new Money(129000), "나이키", 2);

            assertAll(
                () -> assertThat(orderItem.getOrderId()).isEqualTo(1L),
                () -> assertThat(orderItem.getProductId()).isEqualTo(100L),
                () -> assertThat(orderItem.getProductName()).isEqualTo("에어맥스"),
                () -> assertThat(orderItem.getProductPrice()).isEqualTo(new Money(129000)),
                () -> assertThat(orderItem.getBrandName()).isEqualTo("나이키"),
                () -> assertThat(orderItem.getQuantity()).isEqualTo(new Quantity(2))
            );
        }

        @DisplayName("orderId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOrderIdIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new OrderItem(null, 100L, "에어맥스", new Money(129000), "나이키", 2));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductIdIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new OrderItem(1L, null, "에어맥스", new Money(129000), "나이키", 2));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품 이름이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductNameIsBlank() {
            CoreException result = assertThrows(CoreException.class,
                () -> new OrderItem(1L, 100L, "", new Money(129000), "나이키", 2));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품 가격이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductPriceIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new OrderItem(1L, 100L, "에어맥스", null, "나이키", 2));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("브랜드 이름이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBrandNameIsBlank() {
            CoreException result = assertThrows(CoreException.class,
                () -> new OrderItem(1L, 100L, "에어맥스", new Money(129000), "", 2));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsZero() {
            CoreException result = assertThrows(CoreException.class,
                () -> new OrderItem(1L, 100L, "에어맥스", new Money(129000), "나이키", 0));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
