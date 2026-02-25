package com.loopers.application.cart;

import com.loopers.application.product.ProductAppService;
import com.loopers.domain.cart.CartItem;
import com.loopers.domain.common.Money;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("CartFacade 단위 테스트")
class CartFacadeTest {

    private CartFacade cartFacade;
    private CartAppService cartAppService;
    private ProductAppService productAppService;

    @BeforeEach
    void setUp() {
        cartAppService = mock(CartAppService.class);
        productAppService = mock(ProductAppService.class);
        cartFacade = new CartFacade(cartAppService, productAppService);
    }

    @Nested
    @DisplayName("장바구니 담기")
    class AddToCartTest {

        @Test
        @DisplayName("옵션 존재 여부를 확인한 후 장바구니에 추가한다")
        void addToCart_success() {
            // given
            Long userId = 1L;
            Long optionId = 100L;
            int quantity = 2;
            Option option = Option.of(optionId, 10L, "기본 옵션", Money.of(1000L), 50, false);
            CartItem savedItem = CartItem.of(1L, userId, optionId, quantity);

            given(productAppService.getOptionById(optionId)).willReturn(option);
            given(cartAppService.addToCart(userId, optionId, quantity)).willReturn(savedItem);

            // when
            CartItem result = cartFacade.addToCart(userId, optionId, quantity);

            // then
            assertThat(result.getOptionId()).isEqualTo(optionId);
            verify(productAppService).getOptionById(optionId);
            verify(cartAppService).addToCart(userId, optionId, quantity);
        }
    }

    @Nested
    @DisplayName("장바구니 조회")
    class GetCartTest {

        @Test
        @DisplayName("빈 장바구니를 조회하면 빈 결과를 반환한다")
        void getCart_empty() {
            // given
            Long userId = 1L;
            given(cartAppService.getCartItems(userId)).willReturn(List.of());

            // when
            CartInfo result = cartFacade.getCart(userId);

            // then
            assertThat(result.getItems()).isEmpty();
            verify(productAppService, never()).getOptionsByIds(any());
            verify(productAppService, never()).getByIds(any());
        }

        @Test
        @DisplayName("장바구니 항목을 배치 조회로 조합하여 반환한다")
        void getCart_withItems() {
            // given
            Long userId = 1L;
            Long optionId1 = 100L;
            Long optionId2 = 200L;
            Long productId1 = 10L;
            Long productId2 = 20L;

            List<CartItem> cartItems = List.of(
                    CartItem.of(1L, userId, optionId1, 2),
                    CartItem.of(2L, userId, optionId2, 1)
            );

            Option option1 = Option.of(optionId1, productId1, "옵션A", Money.of(1000L), 50, false);
            Option option2 = Option.of(optionId2, productId2, "옵션B", Money.of(2000L), 30, false);
            Map<Long, Option> optionMap = Map.of(optionId1, option1, optionId2, option2);

            Product product1 = Product.of(productId1, 1L, "상품A", Money.of(10000L), false);
            Product product2 = Product.of(productId2, 1L, "상품B", Money.of(20000L), false);
            Map<Long, Product> productMap = Map.of(productId1, product1, productId2, product2);

            given(cartAppService.getCartItems(userId)).willReturn(cartItems);
            given(productAppService.getOptionsByIds(List.of(optionId1, optionId2))).willReturn(optionMap);
            given(productAppService.getByIds(any())).willReturn(productMap);

            // when
            CartInfo result = cartFacade.getCart(userId);

            // then
            assertThat(result.getItems()).hasSize(2);
            verify(productAppService).getOptionsByIds(List.of(optionId1, optionId2));
        }

        @Test
        @DisplayName("품절된 옵션의 장바구니 항목은 orderable=false로 반환된다")
        void getCart_soldOutOption() {
            // given
            Long userId = 1L;
            Long optionId = 100L;
            Long productId = 10L;

            List<CartItem> cartItems = List.of(CartItem.of(1L, userId, optionId, 5));
            Option soldOutOption = Option.of(optionId, productId, "품절 옵션", Money.of(0L), 0, false);
            Product product = Product.of(productId, 1L, "상품", Money.of(10000L), false);

            given(cartAppService.getCartItems(userId)).willReturn(cartItems);
            given(productAppService.getOptionsByIds(List.of(optionId))).willReturn(Map.of(optionId, soldOutOption));
            given(productAppService.getByIds(any())).willReturn(Map.of(productId, product));

            // when
            CartInfo result = cartFacade.getCart(userId);

            // then
            assertThat(result.getItems().get(0).isOrderable()).isFalse();
        }
    }

    @Nested
    @DisplayName("수량 변경")
    class UpdateQuantityTest {

        @Test
        @DisplayName("CartAppService에 위임한다")
        void updateQuantity() {
            // given
            Long userId = 1L;
            Long cartItemId = 1L;
            int quantity = 5;
            CartItem updatedItem = CartItem.of(cartItemId, userId, 100L, quantity);

            given(cartAppService.updateQuantity(userId, cartItemId, quantity)).willReturn(updatedItem);

            // when
            CartItem result = cartFacade.updateQuantity(userId, cartItemId, quantity);

            // then
            assertThat(result.getQuantity()).isEqualTo(5);
            verify(cartAppService).updateQuantity(userId, cartItemId, quantity);
        }
    }

    @Nested
    @DisplayName("삭제")
    class DeleteTest {

        @Test
        @DisplayName("CartAppService에 위임한다")
        void delete() {
            // given
            Long userId = 1L;
            Long cartItemId = 1L;

            // when
            cartFacade.delete(userId, cartItemId);

            // then
            verify(cartAppService).delete(userId, cartItemId);
        }
    }
}
