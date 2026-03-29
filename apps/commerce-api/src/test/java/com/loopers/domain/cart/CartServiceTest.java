package com.loopers.domain.cart;

import com.loopers.domain.outbox.TransactionalOutboxWriter;
import com.loopers.domain.product.Quantity;
import com.loopers.domain.cart.CartItemModel;
import com.loopers.domain.cart.CartRepository;
import com.loopers.domain.product.ProductService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long CART_ITEM_ID = 10L;
    private static final Long PRODUCT_ID = 100L;
    private static final Long OPTION_ID = 5L;
    private static final Long NON_EXISTENT_CART_ITEM_ID = 999L;
    private static final int QUANTITY = 2;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductService productService;

    @Mock
    private TransactionalOutboxWriter transactionalOutboxWriter;

    @InjectMocks
    private CartService cartService;

    @DisplayName("addItem 시")
    @Nested
    class AddItem {

        @DisplayName("동일 품목이 없으면 검증 후 새 항목을 저장한다.")
        @Test
        void addItem_whenNoExistingSameProduct_shouldCreateAndSave() {
            // given
            when(cartRepository.findByUserId(USER_ID)).thenReturn(List.of());
            CartItemModel newItem = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, Quantity.of(QUANTITY));
            when(cartRepository.save(any(CartItemModel.class))).thenReturn(newItem);

            // when
            CartItemModel result = cartService.addItem(USER_ID, PRODUCT_ID, OPTION_ID, QUANTITY);

            // then
            verify(productService).validateProductAvailability(PRODUCT_ID, Quantity.of(QUANTITY), OPTION_ID);
            verify(cartRepository).save(any(CartItemModel.class));
            assertThat(result.getProductId()).isEqualTo(PRODUCT_ID);
        }

        @DisplayName("동일 품목이 있으면 수량을 합산하여 저장한다.")
        @Test
        void addItem_whenExistingSameProduct_shouldMergeAndSave() {
            // given
            CartItemModel existing = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, Quantity.of(1));
            when(cartRepository.findByUserId(USER_ID)).thenReturn(List.of(existing));
            when(cartRepository.save(existing)).thenReturn(existing);

            // when
            cartService.addItem(USER_ID, PRODUCT_ID, OPTION_ID, QUANTITY);

            // then
            verify(productService).validateProductAvailability(PRODUCT_ID, Quantity.of(QUANTITY), OPTION_ID);
            verify(productService).validateProductAvailability(PRODUCT_ID, Quantity.of(3), OPTION_ID);
            verify(cartRepository).save(existing);
            assertThat(existing.getQuantity()).isEqualTo(3);
        }

        @DisplayName("상품 검증 실패 시 예외가 전파되고 저장소 write는 수행되지 않는다.")
        @Test
        void addItem_whenProductValidationFails_shouldThrowAndNotSave() {
            // given: addItem은 먼저 validateProductAvailability를 호출하므로, 실패 시 repository는 호출되지 않음
            doThrow(new CoreException(ErrorType.NOT_FOUND, "상품 없음"))
                    .when(productService).validateProductAvailability(PRODUCT_ID, Quantity.of(QUANTITY), OPTION_ID);

            // when & then
            assertThrows(CoreException.class, () -> cartService.addItem(USER_ID, PRODUCT_ID, OPTION_ID, QUANTITY));
            verify(cartRepository, never()).findByUserId(any());
            verify(cartRepository, never()).save(any(CartItemModel.class));
        }
    }

    @DisplayName("getItems 시")
    @Nested
    class GetItems {

        @DisplayName("해당 사용자의 장바구니 목록을 반환한다.")
        @Test
        void getItems_shouldReturnList() {
            // given
            List<CartItemModel> items = List
                    .of(CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, Quantity.of(QUANTITY)));
            when(cartRepository.findByUserId(USER_ID)).thenReturn(items);

            // when
            List<CartItemModel> result = cartService.getItems(USER_ID);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getProductId()).isEqualTo(PRODUCT_ID);
        }
    }

    @DisplayName("updateItem 시")
    @Nested
    class UpdateItem {

        @DisplayName("항목이 없으면 NOT_FOUND 예외가 발생하고 save는 수행되지 않는다.")
        @Test
        void updateItem_whenItemNotFound_shouldThrowNotFound() {
            // given
            when(cartRepository.findByUserIdAndCartItemId(USER_ID, CART_ITEM_ID)).thenReturn(Optional.empty());

            // when & then
            CoreException ex = assertThrows(CoreException.class,
                    () -> cartService.updateItem(USER_ID, CART_ITEM_ID, 3, OPTION_ID));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(cartRepository, never()).save(any());
        }

        @DisplayName("유효한 요청이면 검증 후 수량·옵션을 갱신한다.")
        @Test
        void updateItem_whenValid_shouldUpdateAndSave() {
            // given
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, Quantity.of(QUANTITY));
            when(cartRepository.findByUserIdAndCartItemId(USER_ID, CART_ITEM_ID)).thenReturn(Optional.of(item));
            when(cartRepository.save(item)).thenReturn(item);

            // when
            CartItemModel result = cartService.updateItem(USER_ID, CART_ITEM_ID, 5, 20L);

            // then
            verify(productService).validateProductAvailability(PRODUCT_ID, Quantity.of(5), 20L);
            verify(cartRepository).save(item);
            assertThat(item.getQuantity()).isEqualTo(5);
            assertThat(item.getOptionId()).isEqualTo(20L);
        }

        @DisplayName("상품 검증 실패 시 예외가 전파되고 save는 수행되지 않는다.")
        @Test
        void updateItem_whenProductValidationFails_shouldThrowAndNotSave() {
            // given
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, Quantity.of(QUANTITY));
            when(cartRepository.findByUserIdAndCartItemId(USER_ID, CART_ITEM_ID)).thenReturn(Optional.of(item));
            doThrow(new CoreException(ErrorType.BAD_REQUEST, "재고 부족"))
                    .when(productService).validateProductAvailability(PRODUCT_ID, Quantity.of(10), 20L);

            // when & then
            assertThrows(CoreException.class, () -> cartService.updateItem(USER_ID, CART_ITEM_ID, 10, 20L));
            verify(cartRepository, never()).save(any());
        }
    }

    @DisplayName("removeItems 시")
    @Nested
    class RemoveItems {

        @DisplayName("빈 목록이면 아무 작업도 하지 않는다.")
        @Test
        void removeItems_whenEmpty_shouldDoNothing() {
            // when
            cartService.removeItems(USER_ID, List.of());
            // then
            verify(cartRepository, never()).delete(any());
        }

        @DisplayName("null 목록이면 아무 작업도 하지 않는다.")
        @Test
        void removeItems_whenNull_shouldDoNothing() {
            // when
            cartService.removeItems(USER_ID, null);
            // then
            verify(cartRepository, never()).delete(any());
        }

        @DisplayName("항목이 없으면 NOT_FOUND 예외가 발생하고 delete는 수행되지 않는다.")
        @Test
        void removeItems_whenItemNotFound_shouldThrowNotFound() {
            // given
            when(cartRepository.findByUserIdAndCartItemId(USER_ID, CART_ITEM_ID)).thenReturn(Optional.empty());

            // when & then
            CoreException ex = assertThrows(CoreException.class,
                    () -> cartService.removeItems(USER_ID, List.of(CART_ITEM_ID)));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(cartRepository, never()).delete(any());
        }

        @DisplayName("존재하는 항목이면 삭제한다.")
        @Test
        void removeItems_whenExists_shouldDelete() {
            // given
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, Quantity.of(QUANTITY));
            when(cartRepository.findByUserIdAndCartItemId(USER_ID, CART_ITEM_ID)).thenReturn(Optional.of(item));

            // when
            cartService.removeItems(USER_ID, List.of(CART_ITEM_ID));

            // then
            verify(cartRepository).delete(item);
        }

        @DisplayName("복수 ID 중 하나라도 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void removeItems_whenOneOfMultipleNotFound_shouldThrowNotFound() {
            // given: 첫 번째는 존재, 두 번째는 없음 → 첫 번째 delete 후 두 번째 조회 시 예외
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, Quantity.of(QUANTITY));
            when(cartRepository.findByUserIdAndCartItemId(USER_ID, CART_ITEM_ID)).thenReturn(Optional.of(item));
            when(cartRepository.findByUserIdAndCartItemId(USER_ID, NON_EXISTENT_CART_ITEM_ID))
                    .thenReturn(Optional.empty());

            // when & then
            CoreException ex = assertThrows(CoreException.class,
                    () -> cartService.removeItems(USER_ID, List.of(CART_ITEM_ID, NON_EXISTENT_CART_ITEM_ID)));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            assertThat(ex.getMessage()).contains(String.valueOf(NON_EXISTENT_CART_ITEM_ID));
            verify(cartRepository).delete(item);
        }
    }
}
