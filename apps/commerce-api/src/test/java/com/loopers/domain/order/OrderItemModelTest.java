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

class OrderItemModelTest {

    @DisplayName("주문 상품 생성")
    @Nested
    class Create {

        @DisplayName("유효한 정보로 생성할 수 있다")
        @Test
        void createsWithValidInput() {
            // given
            Long orderId = 1L;
            Long productId = 2L;
            String productName = "에어맥스 90";
            Money productPrice = new Money(129000);
            int quantity = 2;

            // when
            OrderItemModel item = new OrderItemModel(orderId, productId, productName, productPrice, quantity);

            // then
            assertAll(
                () -> assertThat(item.orderId()).isEqualTo(orderId),
                () -> assertThat(item.productId()).isEqualTo(productId),
                () -> assertThat(item.productName()).isEqualTo(productName),
                () -> assertThat(item.productPrice()).isEqualTo(productPrice),
                () -> assertThat(item.quantity()).isEqualTo(quantity)
            );
        }

        @DisplayName("orderId가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsWhenOrderIdNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new OrderItemModel(null, 1L, "상품", new Money(1000), 1));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productId가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsWhenProductIdNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new OrderItemModel(1L, null, "상품", new Money(1000), 1));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productName이 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsWhenProductNameNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new OrderItemModel(1L, 1L, null, new Money(1000), 1));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productName이 공백이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsWhenProductNameBlank() {
            CoreException result = assertThrows(CoreException.class,
                () -> new OrderItemModel(1L, 1L, "  ", new Money(1000), 1));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productPrice가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsWhenProductPriceNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new OrderItemModel(1L, 1L, "상품", null, 1));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("quantity가 0이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsWhenQuantityZero() {
            CoreException result = assertThrows(CoreException.class,
                () -> new OrderItemModel(1L, 1L, "상품", new Money(1000), 0));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("소계 계산")
    @Nested
    class Subtotal {

        @DisplayName("가격 * 수량으로 소계를 계산한다")
        @Test
        void calculatesSubtotal() {
            OrderItemModel item = new OrderItemModel(1L, 1L, "에어맥스", new Money(129000), 2);
            assertThat(item.subtotal()).isEqualTo(new Money(258000));
        }

        @DisplayName("수량이 1이면 가격과 소계가 같다")
        @Test
        void subtotalEqualsUnitPriceWhenQuantityIsOne() {
            Money price = new Money(59000);
            OrderItemModel item = new OrderItemModel(1L, 1L, "양말", price, 1);
            assertThat(item.subtotal()).isEqualTo(price);
        }
    }
}
