package com.loopers.domain.cart;

import com.loopers.support.error.CartItemErrorType;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CartItemServiceTest {

    private CartItemRepository cartItemRepository;
    private CartItemService cartItemService;

    @BeforeEach
    void setUp() {
        cartItemRepository = Mockito.mock(CartItemRepository.class);
        cartItemService = new CartItemService(cartItemRepository);
    }

    @DisplayName("장바구니에 추가할 때,")
    @Nested
    class 추가 {

        @Test
        void 이미_존재하는_상품이면_수량이_합산된다() {
            // arrange
            CartItem existing = CartItem.create(1L, 100L, 3);
            when(cartItemRepository.findByUserIdAndProductId(1L, 100L)).thenReturn(Optional.of(existing));

            // act
            CartItem result = cartItemService.addToCart(1L, 100L, 2);

            // assert
            assertThat(result.getQuantity()).isEqualTo(5);
        }

        @Test
        void 새로운_상품이면_새_CartItem이_생성된다() {
            // arrange
            when(cartItemRepository.findByUserIdAndProductId(1L, 100L)).thenReturn(Optional.empty());
            when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            CartItem result = cartItemService.addToCart(1L, 100L, 3);

            // assert
            assertThat(result)
                    .extracting(CartItem::getUserId, CartItem::getProductId, CartItem::getQuantity)
                    .containsExactly(1L, 100L, 3);
        }

        @Test
        void 생성_시_save가_호출된다() {
            // arrange
            when(cartItemRepository.findByUserIdAndProductId(1L, 100L)).thenReturn(Optional.empty());
            when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            cartItemService.addToCart(1L, 100L, 3);

            // assert
            verify(cartItemRepository).save(any(CartItem.class));
        }
    }

    @DisplayName("수량을 변경할 때,")
    @Nested
    class 수량변경 {

        @Test
        void 존재하지_않는_항목이면_예외가_발생한다() {
            // arrange
            when(cartItemRepository.findById(1L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> cartItemService.changeQuantity(1L, 1L, 5))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(CartItemErrorType.CART_ITEM_NOT_FOUND);
        }

        @Test
        void 본인_장바구니가_아니면_예외가_발생한다() {
            // arrange
            CartItem cartItem = CartItem.create(1L, 100L, 3);
            when(cartItemRepository.findById(1L)).thenReturn(Optional.of(cartItem));

            // act & assert
            assertThatThrownBy(() -> cartItemService.changeQuantity(1L, 999L, 5))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(CartItemErrorType.NOT_OWNER);
        }

        @Test
        void 유효한_요청이면_수량이_변경된다() {
            // arrange
            CartItem cartItem = CartItem.create(1L, 100L, 3);
            when(cartItemRepository.findById(1L)).thenReturn(Optional.of(cartItem));

            // act
            cartItemService.changeQuantity(1L, 1L, 5);

            // assert
            assertThat(cartItem.getQuantity()).isEqualTo(5);
        }
    }

    @DisplayName("장바구니를 삭제할 때,")
    @Nested
    class 삭제 {

        @Test
        void 존재하지_않는_항목이면_예외가_발생한다() {
            // arrange
            when(cartItemRepository.findById(1L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> cartItemService.delete(1L, 1L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(CartItemErrorType.CART_ITEM_NOT_FOUND);
        }

        @Test
        void 본인_장바구니가_아니면_예외가_발생한다() {
            // arrange
            CartItem cartItem = CartItem.create(1L, 100L, 3);
            when(cartItemRepository.findById(1L)).thenReturn(Optional.of(cartItem));

            // act & assert
            assertThatThrownBy(() -> cartItemService.delete(1L, 999L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(CartItemErrorType.NOT_OWNER);
        }

        @Test
        void 유효한_요청이면_delete가_호출된다() {
            // arrange
            CartItem cartItem = CartItem.create(1L, 100L, 3);
            when(cartItemRepository.findById(1L)).thenReturn(Optional.of(cartItem));

            // act
            cartItemService.delete(1L, 1L);

            // assert
            verify(cartItemRepository).delete(cartItem);
        }
    }
}
