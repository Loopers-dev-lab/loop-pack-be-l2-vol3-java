package com.loopers.domain.cart;

import com.loopers.domain.Quantity;
import com.loopers.domain.brand.BrandDomainService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductDomainService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class CartDomainServiceIntegrationTest {

    @Autowired
    private CartDomainService cartService;

    @Autowired
    private ProductDomainService productService;

    @Autowired
    private BrandDomainService brandService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long brandId;
    private Long productId;

    @BeforeEach
    void setUp() {
        brandId = brandService.register("나이키").getId();
        Product product = productService.register(brandId, "에어맥스", 129000, 100);
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("장바구니에 상품을 담을 때, ")
    @Nested
    class AddToCart {

        @DisplayName("새로운 상품이면, 장바구니 항목이 생성된다.")
        @Test
        void createsCartItem_whenNewProduct() {
            cartService.addToCart(1L, productId, 2);

            List<CartItem> items = cartService.getCartItems(1L);
            assertAll(
                () -> assertThat(items).hasSize(1),
                () -> assertThat(items.get(0).getProductId()).isEqualTo(productId),
                () -> assertThat(items.get(0).getQuantity()).isEqualTo(new Quantity(2))
            );
        }

        @DisplayName("이미 담긴 상품이면, 수량이 합산된다.")
        @Test
        void addsQuantity_whenProductAlreadyInCart() {
            cartService.addToCart(1L, productId, 2);

            cartService.addToCart(1L, productId, 3);

            List<CartItem> items = cartService.getCartItems(1L);
            assertAll(
                () -> assertThat(items).hasSize(1),
                () -> assertThat(items.get(0).getQuantity()).isEqualTo(new Quantity(5))
            );
        }
    }

    @DisplayName("장바구니 수량을 변경할 때, ")
    @Nested
    class UpdateQuantity {

        @DisplayName("올바른 수량이면, 수량이 변경된다.")
        @Test
        void updatesQuantity_whenValid() {
            cartService.addToCart(1L, productId, 2);
            Long cartItemId = cartService.getCartItems(1L).get(0).getId();

            cartService.updateItemQuantity(1L, cartItemId, 5);

            List<CartItem> items = cartService.getCartItems(1L);
            assertThat(items.get(0).getQuantity()).isEqualTo(new Quantity(5));
        }

        @DisplayName("장바구니가 없으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCartDoesNotExist() {
            CoreException result = assertThrows(CoreException.class,
                () -> cartService.updateItemQuantity(999L, 1L, 5));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 항목이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenItemDoesNotExist() {
            cartService.addToCart(1L, productId, 2);

            CoreException result = assertThrows(CoreException.class,
                () -> cartService.updateItemQuantity(1L, 999L, 5));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("장바구니 항목을 삭제할 때, ")
    @Nested
    class RemoveItem {

        @DisplayName("존재하는 항목이면, 삭제된다.")
        @Test
        void removesItem_whenItemExists() {
            cartService.addToCart(1L, productId, 2);
            Long cartItemId = cartService.getCartItems(1L).get(0).getId();

            cartService.removeItem(1L, cartItemId);

            List<CartItem> items = cartService.getCartItems(1L);
            assertThat(items).isEmpty();
        }

        @DisplayName("장바구니가 없으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCartDoesNotExist() {
            CoreException result = assertThrows(CoreException.class,
                () -> cartService.removeItem(999L, 1L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("장바구니를 조회할 때, ")
    @Nested
    class GetCartItems {

        @DisplayName("항목이 있으면, 목록을 반환한다.")
        @Test
        void returnsItems_whenItemsExist() {
            Product product2 = productService.register(brandId, "에어포스1", 109000, 200);
            cartService.addToCart(1L, productId, 2);
            cartService.addToCart(1L, product2.getId(), 1);

            List<CartItem> result = cartService.getCartItems(1L);

            assertThat(result).hasSize(2);
        }

        @DisplayName("항목이 없으면, 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoItems() {
            List<CartItem> result = cartService.getCartItems(1L);

            assertThat(result).isEmpty();
        }
    }

    @DisplayName("장바구니를 비울 때, ")
    @Nested
    class ClearCart {

        @DisplayName("모든 항목이 삭제된다.")
        @Test
        void clearsAllItems() {
            Product product2 = productService.register(brandId, "에어포스1", 109000, 200);
            cartService.addToCart(1L, productId, 2);
            cartService.addToCart(1L, product2.getId(), 1);

            cartService.clearCart(1L);

            List<CartItem> result = cartService.getCartItems(1L);
            assertThat(result).isEmpty();
        }
    }
}
