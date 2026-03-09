package com.loopers.domain.cart;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class CartServiceIntegrationTest {

    private static final Long TEST_USER_ID = 1L;
    private static final Long NON_EXISTENT_ID = 999_999L;

    @Autowired
    private CartService cartService;

    @Autowired
    private ProductService productService;

    @Autowired
    private BrandService brandService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long saveProduct() {
        BrandModel brand = brandService.registerBrand("테스트 브랜드");
        ProductModel product = productService.registerProduct(brand.getId(), "테스트 상품", new BigDecimal("10000"), 10);
        return product.getId();
    }

    @DisplayName("addItem 시")
    @Nested
    class AddItem {

        @DisplayName("유효한 상품이면 장바구니에 추가된다.")
        @Test
        void addItem_whenValid_shouldSave() {
            // given
            Long productId = saveProduct();

            // when
            CartItemModel saved = cartService.addItem(TEST_USER_ID, productId, null, 2);

            // then
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getUserId()).isEqualTo(TEST_USER_ID);
            assertThat(saved.getProductId()).isEqualTo(productId);
            assertThat(saved.getQuantity()).isEqualTo(2);
        }

        @DisplayName("동일 상품·동일 옵션 추가 시 수량이 합산된다.")
        @Test
        void addItem_whenSameProductAndOption_shouldMergeQuantity() {
            // given
            Long productId = saveProduct();
            cartService.addItem(TEST_USER_ID, productId, 10L, 1);

            // when
            CartItemModel saved = cartService.addItem(TEST_USER_ID, productId, 10L, 2);

            // then
            assertThat(saved.getQuantity()).isEqualTo(3);
            assertThat(cartService.getItems(TEST_USER_ID)).hasSize(1);
        }

        @DisplayName("존재하지 않는 상품이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void addItem_whenProductNotFound_shouldThrowNotFound() {
            // when & then
            CoreException ex = assertThrows(CoreException.class, () ->
                cartService.addItem(TEST_USER_ID, NON_EXISTENT_ID, null, 1));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("재고 부족이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void addItem_whenInsufficientStock_shouldThrowBadRequest() {
            // given - 재고 1인 상품
            BrandModel brand = brandService.registerBrand("브랜드");
            ProductModel product = productService.registerProduct(brand.getId(), "소량 상품", BigDecimal.ONE, 1);
            Long productId = product.getId();

            // when & then
            CoreException ex = assertThrows(CoreException.class, () ->
                cartService.addItem(TEST_USER_ID, productId, null, 10));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("getItems 시")
    @Nested
    class GetItems {

        @DisplayName("해당 사용자의 장바구니 목록을 반환한다.")
        @Test
        void getItems_shouldReturnUserItems() {
            // given
            Long productId = saveProduct();
            cartService.addItem(TEST_USER_ID, productId, null, 1);

            // when
            List<CartItemModel> items = cartService.getItems(TEST_USER_ID);

            // then
            assertThat(items).hasSize(1);
            assertThat(items.get(0).getProductId()).isEqualTo(productId);
        }

        @DisplayName("장바구니가 비어 있으면 빈 목록을 반환한다.")
        @Test
        void getItems_whenEmpty_shouldReturnEmpty() {
            // given - 장바구니에 항목 없음
            // when
            List<CartItemModel> items = cartService.getItems(TEST_USER_ID);

            // then
            assertThat(items).isEmpty();
        }
    }

    @DisplayName("updateItem 시")
    @Nested
    class UpdateItem {

        @DisplayName("유효한 요청이면 수량·옵션을 갱신한다.")
        @Test
        void updateItem_whenValid_shouldUpdate() {
            // given
            Long productId = saveProduct();
            CartItemModel added = cartService.addItem(TEST_USER_ID, productId, 5L, 2);

            // when
            CartItemModel updated = cartService.updateItem(TEST_USER_ID, added.getId(), 5, 10L);

            // then
            assertThat(updated.getQuantity()).isEqualTo(5);
            assertThat(updated.getOptionId()).isEqualTo(10L);
        }

        @DisplayName("항목이 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void updateItem_whenNotFound_shouldThrowNotFound() {
            CoreException ex = assertThrows(CoreException.class, () ->
                cartService.updateItem(TEST_USER_ID, NON_EXISTENT_ID, 1, null));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("removeItems 시")
    @Nested
    class RemoveItems {

        @DisplayName("존재하는 항목이면 삭제된다.")
        @Test
        void removeItems_whenExists_shouldDelete() {
            // given
            Long productId = saveProduct();
            CartItemModel added = cartService.addItem(TEST_USER_ID, productId, null, 1);

            // when
            cartService.removeItems(TEST_USER_ID, List.of(added.getId()));

            // then
            assertThat(cartService.getItems(TEST_USER_ID)).isEmpty();
        }

        @DisplayName("항목이 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void removeItems_whenNotFound_shouldThrowNotFound() {
            // when & then
            CoreException ex = assertThrows(CoreException.class, () ->
                cartService.removeItems(TEST_USER_ID, List.of(NON_EXISTENT_ID)));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
