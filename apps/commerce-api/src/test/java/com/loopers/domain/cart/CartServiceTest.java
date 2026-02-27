package com.loopers.domain.cart;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartService 도메인 서비스 테스트")
class CartServiceTest {

    @Mock
    CartItemRepository cartItemRepository;

    @InjectMocks
    CartService cartService;

    // === 등록 ===

    @Nested
    @DisplayName("장바구니 등록")
    class AddItemTests {

        @Test
        @DisplayName("새 상품을 장바구니에 등록하면 save가 호출된다")
        void addItem_NewItem_ShouldCreate() {
            when(cartItemRepository.findById(new CartItemId("user-1", "product-1")))
                    .thenReturn(Optional.empty());
            when(cartItemRepository.save(any(CartItemModel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            cartService.addItem("user-1", "product-1", 3);

            verify(cartItemRepository).save(any(CartItemModel.class));
        }

        @Test
        @DisplayName("이미 있는 상품 재등록 시 수량이 병합된다")
        void addItem_ExistingItem_ShouldMergeQuantity() {
            CartItemModel existing = CartItemModel.create("user-1", "product-1", 2);
            when(cartItemRepository.findById(new CartItemId("user-1", "product-1")))
                    .thenReturn(Optional.of(existing));

            cartService.addItem("user-1", "product-1", 3);

            assertThat(existing.getQuantity()).isEqualTo(5);
            verify(cartItemRepository, never()).save(any());
        }
    }

    // === 수량 변경 ===

    @Nested
    @DisplayName("수량 변경")
    class ChangeQuantityTests {

        @Test
        @DisplayName("정상적으로 수량이 변경된다")
        void changeQuantity_ShouldUpdate() {
            CartItemModel item = CartItemModel.create("user-1", "product-1", 2);
            when(cartItemRepository.findById(new CartItemId("user-1", "product-1")))
                    .thenReturn(Optional.of(item));

            cartService.changeQuantity("user-1", "product-1", 5);

            assertThat(item.getQuantity()).isEqualTo(5);
        }

        @Test
        @DisplayName("존재하지 않는 항목 수량 변경 시 CART_ITEM_NOT_FOUND 예외가 발생한다")
        void changeQuantity_NonExistingItem_ShouldThrow() {
            when(cartItemRepository.findById(new CartItemId("user-1", "product-1")))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.changeQuantity("user-1", "product-1", 5))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.CART_ITEM_NOT_FOUND));
        }
    }

    // === 삭제 ===

    @Nested
    @DisplayName("장바구니 삭제")
    class RemoveItemTests {

        @Test
        @DisplayName("정상 삭제 시 delete가 호출된다")
        void removeItem_ShouldDelete() {
            CartItemModel item = CartItemModel.create("user-1", "product-1", 3);
            when(cartItemRepository.findById(new CartItemId("user-1", "product-1")))
                    .thenReturn(Optional.of(item));

            cartService.removeItem("user-1", "product-1");

            verify(cartItemRepository).delete(item);
        }

        @Test
        @DisplayName("존재하지 않는 항목 삭제 시 에러 없이 통과한다 (멱등)")
        void removeItem_NonExisting_ShouldBeIdempotent() {
            when(cartItemRepository.findById(new CartItemId("user-1", "product-1")))
                    .thenReturn(Optional.empty());

            assertThatCode(() -> cartService.removeItem("user-1", "product-1"))
                    .doesNotThrowAnyException();
            verify(cartItemRepository, never()).delete(any());
        }
    }

    // === 조회 ===

    @Nested
    @DisplayName("장바구니 항목 조회")
    class GetCartItemsTests {

        @Test
        @DisplayName("사용자의 장바구니 항목 목록을 반환한다")
        void getCartItems_ShouldReturnItemList() {
            CartItemModel item = CartItemModel.create("user-1", "product-1", 2);
            when(cartItemRepository.findAllByUserId("user-1")).thenReturn(List.of(item));

            List<CartItemModel> result = cartService.getCartItems("user-1");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getProductId()).isEqualTo("product-1");
        }
    }

    // === 복원 ===

    @Nested
    @DisplayName("장바구니 복원")
    class RestoreTests {

        @Test
        @DisplayName("주문 취소 시 주문 항목이 장바구니에 복원된다")
        void restoreFromOrder_ShouldCreateCartItems() {
            when(cartItemRepository.findById(new CartItemId("user-1", "product-1")))
                    .thenReturn(Optional.empty());
            when(cartItemRepository.save(any(CartItemModel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            cartService.restoreFromOrder("user-1",
                    List.of(new CartService.RestoreItem("product-1", 3)));

            verify(cartItemRepository).save(any(CartItemModel.class));
        }

        @Test
        @DisplayName("기존 장바구니에 동일 상품이 있으면 수량이 병합된다")
        void restoreFromOrder_ExistingItem_ShouldMergeQuantity() {
            CartItemModel existing = CartItemModel.create("user-1", "product-1", 2);
            when(cartItemRepository.findById(new CartItemId("user-1", "product-1")))
                    .thenReturn(Optional.of(existing));

            cartService.restoreFromOrder("user-1",
                    List.of(new CartService.RestoreItem("product-1", 3)));

            assertThat(existing.getQuantity()).isEqualTo(5);
            verify(cartItemRepository, never()).save(any());
        }
    }
}
