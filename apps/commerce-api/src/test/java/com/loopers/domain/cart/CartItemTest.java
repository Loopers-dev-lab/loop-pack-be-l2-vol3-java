package com.loopers.domain.cart;

import com.loopers.domain.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CartItemTest {

    @DisplayName("CartItem을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 정보이면, CartItem이 생성된다.")
        @Test
        void createsCartItem_whenValidInfo() {
            CartItem cartItem = new CartItem(1L, 100L, 2);

            assertAll(
                () -> assertThat(cartItem.getUserId()).isEqualTo(1L),
                () -> assertThat(cartItem.getProductId()).isEqualTo(100L),
                () -> assertThat(cartItem.getQuantity()).isEqualTo(new Quantity(2))
            );
        }

        @DisplayName("userId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            CoreException result = assertThrows(CoreException.class, () -> new CartItem(null, 100L, 2));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("productId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductIdIsNull() {
            CoreException result = assertThrows(CoreException.class, () -> new CartItem(1L, null, 2));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsZero() {
            CoreException result = assertThrows(CoreException.class, () -> new CartItem(1L, 100L, 0));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("수량을 합산할 때, ")
    @Nested
    class AddQuantity {

        @DisplayName("양수를 합산하면, 수량이 증가한다.")
        @Test
        void addsQuantity_whenAmountIsPositive() {
            CartItem cartItem = new CartItem(1L, 100L, 2);

            cartItem.addQuantity(3);

            assertThat(cartItem.getQuantity()).isEqualTo(new Quantity(5));
        }
    }

    @DisplayName("수량을 변경할 때, ")
    @Nested
    class UpdateQuantity {

        @DisplayName("1 이상이면, 수량이 변경된다.")
        @Test
        void updatesQuantity_whenValueIsPositive() {
            CartItem cartItem = new CartItem(1L, 100L, 2);

            cartItem.updateQuantity(5);

            assertThat(cartItem.getQuantity()).isEqualTo(new Quantity(5));
        }

        @DisplayName("0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsZero() {
            CartItem cartItem = new CartItem(1L, 100L, 2);

            CoreException result = assertThrows(CoreException.class, () -> cartItem.updateQuantity(0));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
