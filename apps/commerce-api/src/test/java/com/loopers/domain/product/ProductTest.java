package com.loopers.domain.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

public class ProductTest {

    private static final String DEFAULT_NAME = "product1";
    private static final Long DEFAULT_REF_BRAND_ID = 105L;
    private static final Integer DEFAULT_PRICE = 1000;
    private static final Integer DEFAULT_STOCK = 100;
    private static final Integer DEFAULT_LIKE_COUNT = 0;

    private static Product createProduct(Long id, String name, Long refBrandId, Integer price, Integer stock, Integer likeCount) {
        return Product.create(id, name, refBrandId, price, stock, likeCount);
    }

    private static Product createProductWithStock(int stock) {
        return createProduct(null, DEFAULT_NAME, DEFAULT_REF_BRAND_ID, DEFAULT_PRICE, stock, DEFAULT_LIKE_COUNT);
    }

    private static void assertCoreException(Runnable runnable, String message) {
        assertThatThrownBy(runnable::run)
            .isInstanceOf(CoreException.class)
            .hasMessage(message);
    }

    @Nested
    @DisplayName("상품 생성")
    class Create {

        @DisplayName("상품 도메인 객체 생성 테스트")
        @Test
        void success_create_product() {
            Product product = createProduct(null, DEFAULT_NAME, DEFAULT_REF_BRAND_ID, DEFAULT_PRICE, DEFAULT_STOCK, DEFAULT_LIKE_COUNT);

            assertThat(product).isNotNull();
            assertThat(product.getId()).isNull();
            assertThat(product.getName()).isEqualTo(DEFAULT_NAME);
            assertThat(product.getRefBrandId()).isEqualTo(DEFAULT_REF_BRAND_ID);
            assertThat(product.getPrice()).isEqualTo(new Money(DEFAULT_PRICE));
            assertThat(product.getStock()).isEqualTo(DEFAULT_STOCK);
            assertThat(product.getLikeCount()).isEqualTo(DEFAULT_LIKE_COUNT);
        }

        @DisplayName("상품 이름이 유효하지 않다면, 생성시 예외를 던진다")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   "})
        void fail_when_invalid_name(String name) {
            assertCoreException(
                () -> createProduct(null, name, DEFAULT_REF_BRAND_ID, DEFAULT_PRICE, DEFAULT_STOCK, DEFAULT_LIKE_COUNT),
                "상품 이름은 필수 입니다"
            );
        }

        @DisplayName("상품의 브랜드 정보가 유효하지 않다면, 생성시 예외를 던진다")
        @ParameterizedTest
        @NullSource
        @ValueSource(longs = {-10000L, -1L, 0L})
        void fail_when_invalid_brand_id(Long refBrandId) {
            assertCoreException(
                () -> createProduct(null, DEFAULT_NAME, refBrandId, DEFAULT_PRICE, DEFAULT_STOCK, DEFAULT_LIKE_COUNT),
                "브랜드FK는 null이거나 0이하가 될 수 없습니다"
            );
        }

        @DisplayName("상품 가격이 null이거나 음수라면, 생성시 예외를 던진다")
        @ParameterizedTest
        @NullSource
        @ValueSource(ints = {-10000, -1})
        void fail_when_invalid_price(Integer price) {
            assertCoreException(
                () -> createProduct(null, DEFAULT_NAME, DEFAULT_REF_BRAND_ID, price, DEFAULT_STOCK, DEFAULT_LIKE_COUNT),
                ErrorMessage.Money.AMOUNT_INVALID
            );
        }

        @DisplayName("상품 재고가 null이거나 0 이하라면, 생성시 예외를 던진다")
        @ParameterizedTest
        @NullSource
        @ValueSource(ints = {-100, -1})
        void fail_when_invalid_stock(Integer stock) {
            assertCoreException(
                () -> createProduct(null, DEFAULT_NAME, DEFAULT_REF_BRAND_ID, DEFAULT_PRICE, stock, DEFAULT_LIKE_COUNT),
                "상품 재고는 null이거나 음수가 될 수 없습니다"
            );
        }

        @DisplayName("좋아요 수가 null이거나 0 미만이라면, 생성시 예외를 던진다")
        @ParameterizedTest
        @NullSource
        @ValueSource(ints = {-100, -1})
        void fail_when_invalid_like_count(Integer likeCount) {
            assertCoreException(
                () -> createProduct(null, DEFAULT_NAME, DEFAULT_REF_BRAND_ID, DEFAULT_PRICE, DEFAULT_STOCK, likeCount),
                "좋아요 수는 null이거나 음수가 될 수 없습니다"
            );
        }
    }

    @Nested
    @DisplayName("재고 검증")
    class StockValidation {

        @DisplayName("요청 수량보다 재고가 많으면 true, 재고가 적으면 false")
        @ParameterizedTest(name = "재고={0}, 요청={1} → {2}")
        @CsvSource({
            "1000, 1, true",
            "1, 1000, false"
        })
        void validate_stock_by_required_quantity(int stock, int requiredQuantity, boolean expected) {
            Product product = createProductWithStock(stock);

            assertThat(product.hasEnoughStock(requiredQuantity)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("재고 차감")
    class StockDecrease {

        @DisplayName("재고 차감에 성공하면, 재고가 요청 수량만큼 줄어든다")
        @Test
        void success_decrease_stock() {
            Product product = createProductWithStock(100);

            product.decreaseStock(30);

            assertThat(product.getStock()).isEqualTo(70);
        }

        @DisplayName("재고보다 많은 수량을 차감하면, 예외를 던진다")
        @Test
        void fail_when_not_enough() {
            Product product = createProductWithStock(10);

            assertCoreException(() -> product.decreaseStock(100), "재고가 부족합니다");
        }

        @DisplayName("재고 차감 수량이 null이거나 양수가 아니면, 예외를 던진다")
        @ParameterizedTest
        @NullSource
        @ValueSource(ints = {-10, -1, 0})
        void fail_when_quantity_invalid(Integer quantity) {
            Product product = createProduct(1L, DEFAULT_NAME, 1L, 10000, 100, 0);

            assertCoreException(() -> product.decreaseStock(quantity), "수량은 양수여야 합니다");
        }
    }

    @Nested
    @DisplayName("좋아요 수 변경")
    class LikeCount {

        @DisplayName("좋아요 수 증가 성공")
        @Test
        void success_increase() {
            Product product = createProduct(null, DEFAULT_NAME, DEFAULT_REF_BRAND_ID, DEFAULT_PRICE, DEFAULT_STOCK, 5);

            product.increaseLikeCount();

            assertThat(product.getLikeCount()).isEqualTo(6);
        }

        @DisplayName("좋아요 수 감소 성공")
        @Test
        void success_decrease() {
            Product product = createProduct(null, DEFAULT_NAME, DEFAULT_REF_BRAND_ID, DEFAULT_PRICE, DEFAULT_STOCK, 5);

            product.decreaseLikeCount();

            assertThat(product.getLikeCount()).isEqualTo(4);
        }

        @DisplayName("좋아요 수가 0일 때 감소하면, 예외를 던진다")
        @Test
        void fail_decrease_when_like_count_is_zero() {
            Product product = createProduct(null, DEFAULT_NAME, DEFAULT_REF_BRAND_ID, DEFAULT_PRICE, DEFAULT_STOCK, 0);

            assertCoreException(() -> product.decreaseLikeCount(), "좋아요 갯수는 음수가 될 수 없습니다");
        }
    }

}
