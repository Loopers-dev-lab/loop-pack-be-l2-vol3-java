package com.loopers.application.cart;

import com.loopers.domain.cart.CartItem;
import com.loopers.domain.cart.CartRepository;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("CartAppService 단위 테스트")
class CartAppServiceTest {

    private CartAppService cartAppService;
    private CartRepository cartRepository;

    @BeforeEach
    void setUp() {
        cartRepository = mock(CartRepository.class);
        cartAppService = new CartAppService(cartRepository);
    }

    @Nested
    @DisplayName("장바구니 추가")
    class AddToCartTest {

        @Test
        @DisplayName("새로운 옵션을 장바구니에 추가하면 새 CartItem을 생성한다")
        void addToCart_newItem() {
            // given
            Long userId = 1L;
            Long optionId = 100L;
            int quantity = 3;
            CartItem savedItem = CartItem.of(1L, userId, optionId, quantity);

            given(cartRepository.findByUserIdAndOptionId(userId, optionId)).willReturn(Optional.empty());
            given(cartRepository.save(any(CartItem.class))).willReturn(savedItem);

            // when
            CartItem result = cartAppService.addToCart(userId, optionId, quantity);

            // then
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getQuantity()).isEqualTo(3);
            verify(cartRepository).save(any(CartItem.class));
        }

        @Test
        @DisplayName("이미 존재하는 옵션을 추가하면 수량이 병합된다")
        void addToCart_existingItem_mergesQuantity() {
            // given
            Long userId = 1L;
            Long optionId = 100L;
            CartItem existingItem = CartItem.of(1L, userId, optionId, 2);
            CartItem mergedItem = CartItem.of(1L, userId, optionId, 5);

            given(cartRepository.findByUserIdAndOptionId(userId, optionId)).willReturn(Optional.of(existingItem));
            given(cartRepository.save(any(CartItem.class))).willReturn(mergedItem);

            // when
            CartItem result = cartAppService.addToCart(userId, optionId, 3);

            // then
            assertThat(existingItem.getQuantity()).isEqualTo(5);
            verify(cartRepository).save(existingItem);
        }
    }

    @Nested
    @DisplayName("장바구니 조회")
    class GetCartItemsTest {

        @Test
        @DisplayName("사용자의 장바구니 항목 목록을 반환한다")
        void getCartItems() {
            // given
            Long userId = 1L;
            List<CartItem> items = List.of(
                    CartItem.of(1L, userId, 100L, 2),
                    CartItem.of(2L, userId, 200L, 1)
            );
            given(cartRepository.findByUserId(userId)).willReturn(items);

            // when
            List<CartItem> result = cartAppService.getCartItems(userId);

            // then
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("장바구니 항목 단건 조회")
    class GetByIdTest {

        @Test
        @DisplayName("ID로 장바구니 항목을 조회할 수 있다")
        void getById_found() {
            // given
            CartItem cartItem = CartItem.of(1L, 1L, 100L, 2);
            given(cartRepository.findById(1L)).willReturn(Optional.of(cartItem));

            // when
            CartItem result = cartAppService.getById(1L);

            // then
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("존재하지 않는 ID로 조회하면 예외가 발생한다")
        void getById_notFound() {
            // given
            given(cartRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> cartAppService.getById(999L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("장바구니 항목을 찾을 수 없습니다.");
        }
    }

    @Nested
    @DisplayName("수량 변경")
    class UpdateQuantityTest {

        @Test
        @DisplayName("본인의 장바구니 항목 수량을 변경할 수 있다")
        void updateQuantity_success() {
            // given
            Long userId = 1L;
            CartItem cartItem = CartItem.of(1L, userId, 100L, 2);
            CartItem updatedItem = CartItem.of(1L, userId, 100L, 5);

            given(cartRepository.findById(1L)).willReturn(Optional.of(cartItem));
            given(cartRepository.save(any(CartItem.class))).willReturn(updatedItem);

            // when
            CartItem result = cartAppService.updateQuantity(userId, 1L, 5);

            // then
            assertThat(cartItem.getQuantity()).isEqualTo(5);
            verify(cartRepository).save(cartItem);
        }

        @Test
        @DisplayName("타인의 장바구니 항목을 수정하면 예외가 발생한다")
        void updateQuantity_notOwner() {
            // given
            CartItem cartItem = CartItem.of(1L, 1L, 100L, 2);
            given(cartRepository.findById(1L)).willReturn(Optional.of(cartItem));

            // when & then
            assertThatThrownBy(() -> cartAppService.updateQuantity(999L, 1L, 5))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("본인의 장바구니 항목만 수정할 수 있습니다.");
        }
    }

    @Nested
    @DisplayName("장바구니 삭제")
    class DeleteTest {

        @Test
        @DisplayName("본인의 장바구니 항목을 삭제할 수 있다")
        void delete_success() {
            // given
            Long userId = 1L;
            CartItem cartItem = CartItem.of(1L, userId, 100L, 2);
            given(cartRepository.findById(1L)).willReturn(Optional.of(cartItem));

            // when
            cartAppService.delete(userId, 1L);

            // then
            verify(cartRepository).delete(cartItem);
        }

        @Test
        @DisplayName("타인의 장바구니 항목을 삭제하면 예외가 발생한다")
        void delete_notOwner() {
            // given
            CartItem cartItem = CartItem.of(1L, 1L, 100L, 2);
            given(cartRepository.findById(1L)).willReturn(Optional.of(cartItem));

            // when & then
            assertThatThrownBy(() -> cartAppService.delete(999L, 1L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("본인의 장바구니 항목만 수정할 수 있습니다.");
        }
    }
}
