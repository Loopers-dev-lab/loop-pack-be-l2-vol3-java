package com.loopers.domain.cart;

import com.loopers.domain.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CartTest {

    @DisplayName("Cart를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 유저 ID이면, Cart가 생성된다.")
        @Test
        void createsCart_whenUserIdIsValid() {
            Cart cart = new Cart(1L);

            assertAll(
                () -> assertThat(cart.getUserId()).isEqualTo(1L),
                () -> assertThat(cart.getItems()).isEmpty()
            );
        }

        @DisplayName("유저 ID가 null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenUserIdIsNull() {
            assertThrows(NullPointerException.class, () -> new Cart(null));
        }
    }

    @DisplayName("상품을 담을 때, ")
    @Nested
    class AddItem {

        @DisplayName("새로운 상품이면, 항목이 추가된다.")
        @Test
        void addsItem_whenNewProduct() {
            Cart cart = new Cart(1L);

            cart.addItem(100L, 2);

            assertAll(
                () -> assertThat(cart.getItems()).hasSize(1),
                () -> assertThat(cart.getItems().get(0).getProductId()).isEqualTo(100L),
                () -> assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(new Quantity(2))
            );
        }

        @DisplayName("이미 담긴 상품이면, 수량이 합산된다.")
        @Test
        void addsQuantity_whenProductAlreadyExists() {
            Cart cart = new Cart(1L);
            cart.addItem(100L, 2);

            cart.addItem(100L, 3);

            assertAll(
                () -> assertThat(cart.getItems()).hasSize(1),
                () -> assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(new Quantity(5))
            );
        }

        @DisplayName("다른 상품이면, 별도 항목으로 추가된다.")
        @Test
        void addsSeparateItem_whenDifferentProduct() {
            Cart cart = new Cart(1L);
            cart.addItem(100L, 2);

            cart.addItem(200L, 3);

            assertThat(cart.getItems()).hasSize(2);
        }
    }

    @DisplayName("항목을 삭제할 때, ")
    @Nested
    class RemoveItem {

        @DisplayName("존재하는 항목이면, 삭제된다.")
        @Test
        void removesItem_whenItemExists() throws Exception {
            Cart cart = new Cart(1L);
            cart.addItem(100L, 2);

            CartItem cartItem = cart.getItems().get(0);
            Field idField = CartItem.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(cartItem, 1L);

            cart.removeItem(1L);

            assertThat(cart.getItems()).isEmpty();
        }

        @DisplayName("존재하지 않는 항목이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenItemDoesNotExist() {
            Cart cart = new Cart(1L);

            CoreException result = assertThrows(CoreException.class, () -> cart.removeItem(999L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("수량을 변경할 때, ")
    @Nested
    class UpdateItemQuantity {

        @DisplayName("존재하지 않는 항목이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenItemDoesNotExist() {
            Cart cart = new Cart(1L);

            CoreException result = assertThrows(CoreException.class,
                () -> cart.updateItemQuantity(999L, 5));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("장바구니를 비울 때, ")
    @Nested
    class Clear {

        @DisplayName("모든 항목이 삭제된다.")
        @Test
        void clearsAllItems() {
            Cart cart = new Cart(1L);
            cart.addItem(100L, 2);
            cart.addItem(200L, 3);

            cart.clear();

            assertThat(cart.getItems()).isEmpty();
        }
    }

    @DisplayName("사용 불가능한 상품을 제거할 때, ")
    @Nested
    class RemoveUnavailableItems {

        @DisplayName("존재하지 않는 상품이 제거된다.")
        @Test
        void removesUnavailableItems() {
            Cart cart = new Cart(1L);
            cart.addItem(100L, 2);
            cart.addItem(200L, 3);
            cart.addItem(300L, 1);

            cart.removeUnavailableItems(Set.of(100L, 300L));

            assertAll(
                () -> assertThat(cart.getItems()).hasSize(2),
                () -> assertThat(cart.getItems().get(0).getProductId()).isEqualTo(100L),
                () -> assertThat(cart.getItems().get(1).getProductId()).isEqualTo(300L)
            );
        }

        @DisplayName("모든 상품이 사용 불가능하면, 장바구니가 비워진다.")
        @Test
        void clearsCart_whenAllItemsUnavailable() {
            Cart cart = new Cart(1L);
            cart.addItem(100L, 2);

            cart.removeUnavailableItems(Set.of(200L));

            assertThat(cart.getItems()).isEmpty();
        }
    }
}
