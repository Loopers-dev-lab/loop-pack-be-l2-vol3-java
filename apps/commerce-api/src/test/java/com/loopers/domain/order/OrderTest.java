package com.loopers.domain.order;

import com.loopers.domain.product.Money;
import com.loopers.domain.product.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long PRODUCT_ID = 10L;
    private static final Quantity VALID_QUANTITY = new Quantity(2);
    private static final Money VALID_PRICE = new Money(10000);
    private static final Money ZERO_DISCOUNT = new Money(0);
    private static final String PRODUCT_NAME = "나이키 에어맥스";
    private static final String BRAND_NAME = "나이키";

    private OrderItem validOrderItem() {
        return new OrderItem(PRODUCT_ID, VALID_QUANTITY, PRODUCT_NAME, BRAND_NAME, VALID_PRICE);
    }

    private Order validOrder(Long userId) {
        Money originalAmount = new Money(VALID_PRICE.getAmount() * VALID_QUANTITY.getValue());
        return new Order(userId, List.of(validOrderItem()), null, originalAmount, ZERO_DISCOUNT);
    }

    @DisplayName("Order 생성 시")
    @Nested
    class Create {

        @DisplayName("정상적인 userId와 orderItems로 Order가 생성된다.")
        @Test
        void createsOrder_whenValidParameters() {
            // act
            Order order = validOrder(USER_ID);

            // assert
            assertThat(order.getUserId()).isEqualTo(USER_ID);
            assertThat(order.getOrderItems()).hasSize(1);
            assertThat(order.getOriginalAmount().getAmount()).isEqualTo(20000);
            assertThat(order.getDiscountAmount().getAmount()).isEqualTo(0);
            assertThat(order.getFinalAmount().getAmount()).isEqualTo(20000);
        }

        @DisplayName("userId가 null이면 BAD_REQUEST 에러가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> new Order(null, List.of(validOrderItem()), null, VALID_PRICE, ZERO_DISCOUNT));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("orderItems가 비어있으면 BAD_REQUEST 에러가 발생한다. (BR-O01)")
        @Test
        void throwsBadRequest_whenOrderItemsIsEmpty() {
            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> new Order(USER_ID, List.of(), null, VALID_PRICE, ZERO_DISCOUNT));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("Order.isOwnedBy() 시")
    @Nested
    class IsOwnedBy {

        @DisplayName("주문 소유자의 userId이면 true를 반환한다.")
        @Test
        void returnsTrue_whenUserIdMatches() {
            // arrange
            Order order = validOrder(USER_ID);

            // act & assert
            assertThat(order.isOwnedBy(USER_ID)).isTrue();
        }

        @DisplayName("주문 소유자가 아닌 userId이면 false를 반환한다. (BR-O06)")
        @Test
        void returnsFalse_whenUserIdDoesNotMatch() {
            // arrange
            Order order = validOrder(USER_ID);

            // act & assert
            assertThat(order.isOwnedBy(OTHER_USER_ID)).isFalse();
        }
    }

    @DisplayName("OrderItem 생성 시")
    @Nested
    class CreateOrderItem {

        @DisplayName("정상적인 파라미터로 OrderItem이 생성된다.")
        @Test
        void createsOrderItem_whenValidParameters() {
            // act
            OrderItem item = new OrderItem(PRODUCT_ID, VALID_QUANTITY, PRODUCT_NAME, BRAND_NAME, VALID_PRICE);

            // assert
            assertThat(item.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(item.getProductName()).isEqualTo(PRODUCT_NAME);
            assertThat(item.getBrandName()).isEqualTo(BRAND_NAME);
        }

        @DisplayName("productId가 null이면 BAD_REQUEST 에러가 발생한다.")
        @Test
        void throwsBadRequest_whenProductIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> new OrderItem(null, VALID_QUANTITY, PRODUCT_NAME, BRAND_NAME, VALID_PRICE));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productName이 blank이면 BAD_REQUEST 에러가 발생한다.")
        @Test
        void throwsBadRequest_whenProductNameIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> new OrderItem(PRODUCT_ID, VALID_QUANTITY, "  ", BRAND_NAME, VALID_PRICE));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("brandName이 blank이면 BAD_REQUEST 에러가 발생한다.")
        @Test
        void throwsBadRequest_whenBrandNameIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> new OrderItem(PRODUCT_ID, VALID_QUANTITY, PRODUCT_NAME, "  ", VALID_PRICE));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("price가 null이면 BAD_REQUEST 에러가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> new OrderItem(PRODUCT_ID, VALID_QUANTITY, PRODUCT_NAME, BRAND_NAME, null));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
