package com.loopers.domain.cart;

import com.loopers.support.error.CartItemErrorType;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CartItemTest {

    @DisplayName("생성할 때,")
    @Nested
    class 생성 {

        @Test
        void 수량이_0_이하이면_예외가_발생한다() {
            // act & assert
            assertThatThrownBy(() -> CartItem.create(1L, 100L, 0))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(CartItemErrorType.INVALID_QUANTITY);
        }

        @Test
        void 유효한_정보면_userId_productId_quantity가_저장된다() {
            // act
            CartItem cartItem = CartItem.create(1L, 100L, 3);

            // assert
            assertThat(cartItem)
                    .extracting(CartItem::getUserId, CartItem::getProductId, CartItem::getQuantity)
                    .containsExactly(1L, 100L, 3);
        }
    }

    @DisplayName("수량을 추가할 때,")
    @Nested
    class 수량추가 {

        @Test
        void 기존_수량에_추가_수량이_합산된다() {
            // arrange
            CartItem cartItem = CartItem.create(1L, 100L, 3);

            // act
            cartItem.addQuantity(2);

            // assert
            assertThat(cartItem.getQuantity()).isEqualTo(5);
        }
    }

    @DisplayName("수량을 변경할 때,")
    @Nested
    class 수량변경 {

        @Test
        void 유효하지_않은_수량이면_예외가_발생한다() {
            // arrange
            CartItem cartItem = CartItem.create(1L, 100L, 3);

            // act & assert
            assertThatThrownBy(() -> cartItem.changeQuantity(0))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(CartItemErrorType.INVALID_QUANTITY);
        }

        @Test
        void 유효한_수량이면_변경된다() {
            // arrange
            CartItem cartItem = CartItem.create(1L, 100L, 3);

            // act
            cartItem.changeQuantity(5);

            // assert
            assertThat(cartItem.getQuantity()).isEqualTo(5);
        }
    }

    @DisplayName("소유권을 확인할 때,")
    @Nested
    class 소유권확인 {

        @Test
        void 본인의_장바구니가_아니면_예외가_발생한다() {
            // arrange
            CartItem cartItem = CartItem.create(1L, 100L, 3);

            // act & assert
            assertThatThrownBy(() -> cartItem.validateOwnership(999L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(CartItemErrorType.NOT_OWNER);
        }

        @Test
        void 본인의_장바구니이면_예외가_발생하지_않는다() {
            // arrange
            CartItem cartItem = CartItem.create(1L, 100L, 3);

            // act & assert
            assertThatCode(() -> cartItem.validateOwnership(1L)).doesNotThrowAnyException();
        }
    }
}
