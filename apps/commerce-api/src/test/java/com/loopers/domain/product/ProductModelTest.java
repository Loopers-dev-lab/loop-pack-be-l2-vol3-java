package com.loopers.domain.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductModelTest {

    private static final Long BRAND_ID = 1L;
    private static final String NAME = "테스트 상품";
    private static final BigDecimal PRICE = new BigDecimal("10000");
    private static final int STOCK = 10;

    @DisplayName("create 시")
    @Nested
    class Create {

        @DisplayName("유효한 값이 주어지면 생성된다.")
        @Test
        void create_withValidInputs_shouldSucceed() {
            // when
            ProductModel product = ProductModel.create(BRAND_ID, NAME, PRICE, STOCK);

            // then
            assertThat(product.getBrandId()).isEqualTo(BRAND_ID);
            assertThat(product.getName()).isEqualTo(NAME);
            assertThat(product.getPrice()).isEqualByComparingTo(PRICE);
            assertThat(product.getStockQuantity()).isEqualTo(STOCK);
            assertThat(product.isDeleted()).isFalse();
        }

        @DisplayName("brandId가 null이면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withNullBrandId_shouldThrow() {
            assertThrows(IllegalArgumentException.class, () ->
                ProductModel.create(null, NAME, PRICE, STOCK));
        }

        @DisplayName("name이 null이면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withNullName_shouldThrow() {
            assertThrows(IllegalArgumentException.class, () ->
                ProductModel.create(BRAND_ID, null, PRICE, STOCK));
        }

        @DisplayName("name이 빈 문자열이면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withBlankName_shouldThrow() {
            assertThrows(IllegalArgumentException.class, () ->
                ProductModel.create(BRAND_ID, "", PRICE, STOCK));
        }

        @DisplayName("price가 null이면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withNullPrice_shouldThrow() {
            assertThrows(IllegalArgumentException.class, () ->
                ProductModel.create(BRAND_ID, NAME, null, STOCK));
        }

        @DisplayName("price가 음수면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withNegativePrice_shouldThrow() {
            assertThrows(IllegalArgumentException.class, () ->
                ProductModel.create(BRAND_ID, NAME, new BigDecimal("-1"), STOCK));
        }

        @DisplayName("stockQuantity가 음수면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withNegativeStock_shouldThrow() {
            assertThrows(IllegalArgumentException.class, () ->
                ProductModel.create(BRAND_ID, NAME, PRICE, -1));
        }

        @DisplayName("가격 0과 재고 0은 허용된다.")
        @Test
        void create_withZeroPriceAndStock_shouldSucceed() {
            ProductModel product = ProductModel.create(BRAND_ID, "무료상품", BigDecimal.ZERO, 0);
            assertThat(product.getPrice()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(product.getStockQuantity()).isZero();
        }

        @DisplayName("상품명 앞뒤 공백은 trim되어 저장된다.")
        @Test
        void create_withLeadingTrailingSpaces_shouldTrimName() {
            ProductModel product = ProductModel.create(BRAND_ID, "  상품  ", PRICE, STOCK);
            assertThat(product.getName()).isEqualTo("상품");
        }

        @DisplayName("상품명이 공백만 있으면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withWhitespaceOnlyName_shouldThrow() {
            assertThrows(IllegalArgumentException.class, () ->
                ProductModel.create(BRAND_ID, "   ", PRICE, STOCK));
        }
    }

    @DisplayName("hasStock 시")
    @Nested
    class HasStock {

        @DisplayName("재고가 수량 이상이면 true를 반환한다.")
        @Test
        void hasStock_whenStockGreaterOrEqual_shouldReturnTrue() {
            ProductModel product = ProductModel.create(BRAND_ID, NAME, PRICE, 10);
            assertThat(product.hasStock(0)).isTrue();
            assertThat(product.hasStock(10)).isTrue();
            assertThat(product.hasStock(5)).isTrue();
        }

        @DisplayName("재고가 수량 미만이면 false를 반환한다.")
        @Test
        void hasStock_whenStockLessThanQuantity_shouldReturnFalse() {
            ProductModel product = ProductModel.create(BRAND_ID, NAME, PRICE, 10);
            assertThat(product.hasStock(11)).isFalse();
        }

        @DisplayName("수량이 음수면 IllegalArgumentException이 발생한다.")
        @Test
        void hasStock_withNegativeQuantity_shouldThrow() {
            ProductModel product = ProductModel.create(BRAND_ID, NAME, PRICE, STOCK);
            assertThrows(IllegalArgumentException.class, () -> product.hasStock(-1));
        }
    }

    @DisplayName("snapshotForOrder 시")
    @Nested
    class SnapshotForOrder {

        @DisplayName("상품명과 가격이 포함된 스냅샷을 반환한다.")
        @Test
        void snapshotForOrder_shouldReturnNameAndPrice() {
            ProductModel product = ProductModel.create(BRAND_ID, "스냅샷상품", new BigDecimal("9999"), 1);
            ProductSnapshot snapshot = product.snapshotForOrder();
            assertThat(snapshot.productName()).isEqualTo("스냅샷상품");
            assertThat(snapshot.price()).isEqualByComparingTo(new BigDecimal("9999"));
            assertThat(snapshot.productId()).isEqualTo(product.getId());
        }
    }

    @DisplayName("삭제 여부 확인 시")
    @Nested
    class IsDeleted {

        @DisplayName("생성 직후에는 삭제되지 않은 상태이다.")
        @Test
        void isDeleted_whenNotDeleted_shouldReturnFalse() {
            ProductModel product = ProductModel.create(BRAND_ID, NAME, PRICE, STOCK);
            assertThat(product.isDeleted()).isFalse();
        }

        @DisplayName("delete() 호출 후에는 삭제된 상태이다.")
        @Test
        void isDeleted_afterDelete_shouldReturnTrue() {
            ProductModel product = ProductModel.create(BRAND_ID, NAME, PRICE, STOCK);
            product.delete();
            assertThat(product.isDeleted()).isTrue();
        }
    }
}
