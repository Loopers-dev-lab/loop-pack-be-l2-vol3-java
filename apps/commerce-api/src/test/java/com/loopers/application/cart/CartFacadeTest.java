package com.loopers.application.cart;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.application.cart.CartInfo;
import com.loopers.domain.cart.CartItemModel;
import com.loopers.domain.cart.CartService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.product.StockService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartFacade 단위 테스트")
class CartFacadeTest {

    private static final Long USER_ID = 1L;

    @Mock CartService cartService;
    @Mock ProductService productService;
    @Mock StockService stockService;
    @Mock BrandService brandService;

    @InjectMocks
    CartFacade cartFacade;

    private ProductModel createTestProduct() {
        return ProductModel.create("테스트상품", 1L, BigDecimal.valueOf(10000),
                "설명", null, null, null, null, null, null);
    }

    private BrandModel createTestBrand() {
        return BrandModel.create("테스트브랜드", "설명", "서울");
    }

    @Nested
    @DisplayName("장바구니 조회")
    class GetCartTests {

        @Test
        @DisplayName("장바구니 항목 + 상품/브랜드/재고 정보를 배치 조회하여 반환한다")
        void getCart_ShouldReturnCartInfoListWithProductInfo() {
            ProductModel product = mock(ProductModel.class);
            when(product.getProductId()).thenReturn(1L);
            when(product.getProductName()).thenReturn("테스트상품");
            when(product.getBrandId()).thenReturn(1L);
            when(product.getPrice()).thenReturn(BigDecimal.valueOf(10000));

            BrandModel brand = mock(BrandModel.class);
            when(brand.getBrandId()).thenReturn(1L);
            when(brand.getBrandName()).thenReturn("테스트브랜드");

            CartItemModel item = CartItemModel.create(USER_ID, 1L, 2);
            when(cartService.getCartItems(USER_ID)).thenReturn(List.of(item));
            when(productService.findAllByIds(anyCollection())).thenReturn(List.of(product));
            when(brandService.findAllByIds(anyCollection())).thenReturn(List.of(brand));
            when(stockService.findAllByProductIds(anyCollection()))
                    .thenReturn(List.of(ProductStockModel.create(1L, 100)));

            List<CartInfo> result = cartFacade.getCart(USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).isAvailable()).isTrue();
            assertThat(result.get(0).getProductName()).isEqualTo("테스트상품");
            verify(cartService).getCartItems(USER_ID);
            verify(productService).findAllByIds(anyCollection());
            verify(brandService).findAllByIds(anyCollection());
            verify(stockService).findAllByProductIds(anyCollection());
        }

        @Test
        @DisplayName("빈 장바구니 조회 시 빈 리스트를 반환한다")
        void getCart_EmptyCart_ShouldReturnEmptyList() {
            when(cartService.getCartItems(USER_ID)).thenReturn(List.of());

            List<CartInfo> result = cartFacade.getCart(USER_ID);

            assertThat(result).isEmpty();
            verify(productService, never()).findAllByIds(anyCollection());
        }
    }

    @Nested
    @DisplayName("장바구니 추가")
    class AddItemTests {

        @Test
        @DisplayName("상품 검증 → 재고 검증 → 장바구니 추가 오케스트레이션이 수행된다")
        void addItem_ShouldOrchestrate() {
            when(productService.findOrderableById(1L)).thenReturn(createTestProduct());
            when(stockService.findByProductId(1L))
                    .thenReturn(ProductStockModel.create(1L, 100));

            cartFacade.addItem(USER_ID, 1L, 3);

            verify(productService).findOrderableById(1L);
            verify(stockService).findByProductId(1L);
            verify(cartService).addItem(USER_ID, 1L, 3);
        }

        @Test
        @DisplayName("가용 재고 초과 시 CART_STOCK_EXCEEDED 예외가 발생한다")
        void addItem_ExceedAvailableStock_ShouldThrow() {
            when(productService.findOrderableById(1L)).thenReturn(createTestProduct());
            when(stockService.findByProductId(1L))
                    .thenReturn(ProductStockModel.create(1L, 5));

            assertThatThrownBy(() -> cartFacade.addItem(USER_ID, 1L, 10))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.CART_STOCK_EXCEEDED));
            verify(cartService, never()).addItem(anyLong(), anyLong(), anyInt());
        }
    }

    @Nested
    @DisplayName("수량 변경")
    class ChangeQuantityTests {

        @Test
        @DisplayName("재고 검증 → 수량 변경 오케스트레이션이 수행된다")
        void changeQuantity_ShouldOrchestrate() {
            when(stockService.findByProductId(1L))
                    .thenReturn(ProductStockModel.create(1L, 100));

            cartFacade.changeQuantity(USER_ID, 1L, 5);

            verify(stockService).findByProductId(1L);
            verify(cartService).changeQuantity(USER_ID, 1L, 5);
        }

        @Test
        @DisplayName("가용 재고 초과 시 CART_STOCK_EXCEEDED 예외가 발생한다")
        void changeQuantity_ExceedAvailableStock_ShouldThrow() {
            when(stockService.findByProductId(1L))
                    .thenReturn(ProductStockModel.create(1L, 3));

            assertThatThrownBy(() -> cartFacade.changeQuantity(USER_ID, 1L, 10))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.CART_STOCK_EXCEEDED));
            verify(cartService, never()).changeQuantity(anyLong(), anyLong(), anyInt());
        }
    }

}
