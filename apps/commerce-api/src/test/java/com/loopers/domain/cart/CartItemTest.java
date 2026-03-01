package com.loopers.domain.cart;

import com.loopers.domain.Quantity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CartItemTest {

    private Cart createCart() {
        return new Cart(1L);
    }

    @DisplayName("CartItem을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 정보이면, CartItem이 생성된다.")
        @Test
        void createsCartItem_whenValidInfo() {
            Cart cart = createCart();
            cart.addItem(100L, 2);

            CartItem cartItem = cart.getItems().get(0);

            assertThat(cartItem.getProductId()).isEqualTo(100L);
            assertThat(cartItem.getQuantity()).isEqualTo(new Quantity(2));
        }
    }

    @DisplayName("수량을 합산할 때, ")
    @Nested
    class AddQuantity {

        @DisplayName("양수를 합산하면, 수량이 증가한다.")
        @Test
        void addsQuantity_whenAmountIsPositive() {
            Cart cart = createCart();
            cart.addItem(100L, 2);

            CartItem cartItem = cart.getItems().get(0);
            cartItem.addQuantity(3);

            assertThat(cartItem.getQuantity()).isEqualTo(new Quantity(5));
        }
    }

    @DisplayName("수량을 변경할 때, ")
    @Nested
    class ChangeQuantity {

        @DisplayName("1 이상이면, 수량이 변경된다.")
        @Test
        void changesQuantity_whenValueIsPositive() {
            Cart cart = createCart();
            cart.addItem(100L, 2);

            CartItem cartItem = cart.getItems().get(0);
            cartItem.changeQuantity(5);

            assertThat(cartItem.getQuantity()).isEqualTo(new Quantity(5));
        }
    }
}
