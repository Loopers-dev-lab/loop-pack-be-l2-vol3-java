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

    private static Money money(BigDecimal value) {
        return Money.of(value);
    }

    private static StockQuantity stock(int value) {
        return StockQuantity.of(value);
    }

    @DisplayName("create 시")
    @Nested
    class Create {

        @Test
        void create_withValidInputs_shouldSucceed() {
            // when
            ProductModel product = ProductModel.create(BRAND_ID, NAME, money(PRICE), stock(STOCK));

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
            assertThrows(IllegalArgumentException.class,
                    () -> ProductModel.create(null, NAME, money(PRICE), stock(STOCK)));
        }

        @DisplayName("name이 null이면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withNullName_shouldThrow() {
            assertThrows(IllegalArgumentException.class,
                    () -> ProductModel.create(BRAND_ID, null, money(PRICE), stock(STOCK)));
        }

        @DisplayName("name이 빈 문자열이면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withBlankName_shouldThrow() {
            assertThrows(IllegalArgumentException.class,
                    () -> ProductModel.create(BRAND_ID, "", money(PRICE), stock(STOCK)));
        }

        @DisplayName("price가 null이면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withNullPrice_shouldThrow() {
            assertThrows(IllegalArgumentException.class, () -> ProductModel.create(BRAND_ID, NAME, null, stock(STOCK)));
        }

        @DisplayName("price가 음수면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withNegativePrice_shouldThrow() {
            assertThrows(IllegalArgumentException.class,
                    () -> ProductModel.create(BRAND_ID, NAME, money(new BigDecimal("-1")), stock(STOCK)));
        }

        @DisplayName("stockQuantity가 음수면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withNegativeStock_shouldThrow() {
            assertThrows(IllegalArgumentException.class,
                    () -> ProductModel.create(BRAND_ID, NAME, money(PRICE), stock(-1)));
        }

        @DisplayName("가격 0과 재고 0은 허용된다.")
        @Test
        void create_withZeroPriceAndStock_shouldSucceed() {
            ProductModel product = ProductModel.create(BRAND_ID, "무료상품", money(BigDecimal.ZERO), stock(0));
            assertThat(product.getPrice()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(product.getStockQuantity()).isZero();
        }

        @DisplayName("상품명 앞뒤 공백은 trim되어 저장된다.")
        @Test
        void create_withLeadingTrailingSpaces_shouldTrimName() {
            ProductModel product = ProductModel.create(BRAND_ID, "  상품  ", money(PRICE), stock(STOCK));
            assertThat(product.getName()).isEqualTo("상품");
        }

        @DisplayName("상품명이 공백만 있으면 IllegalArgumentException이 발생한다.")
        @Test
        void create_withWhitespaceOnlyName_shouldThrow() {
            assertThrows(IllegalArgumentException.class,
                    () -> ProductModel.create(BRAND_ID, "   ", money(PRICE), stock(STOCK)));
        }
    }

    @DisplayName("hasStock 시")
    @Nested
    class HasStock {

        @DisplayName("재고가 수량 이상이면 true를 반환한다.")
        @Test
        void hasStock_whenStockGreaterOrEqual_shouldReturnTrue() {
            ProductModel product = ProductModel.create(BRAND_ID, NAME, money(PRICE), stock(10));
            assertThat(product.hasStock(Quantity.of(10))).isTrue();
            assertThat(product.hasStock(Quantity.of(5))).isTrue();
        }

        @DisplayName("재고가 수량 미만이면 false를 반환한다.")
        @Test
        void hasStock_whenStockLessThanQuantity_shouldReturnFalse() {
            ProductModel product = ProductModel.create(BRAND_ID, NAME, money(PRICE), stock(10));
            assertThat(product.hasStock(Quantity.of(11))).isFalse();
        }

        @DisplayName("수량이 null이면 IllegalArgumentException이 발생한다.")
        @Test
        void hasStock_withNullQuantity_shouldThrow() {
            ProductModel product = ProductModel.create(BRAND_ID, NAME, money(PRICE), stock(STOCK));
            assertThrows(IllegalArgumentException.class, () -> product.hasStock(null));
        }
    }

    @DisplayName("snapshotForOrder 시")
    @Nested
    class SnapshotForOrder {

        @DisplayName("상품명과 가격이 포함된 스냅샷을 반환한다.")
        @Test
        void snapshotForOrder_shouldReturnNameAndPrice() {
            ProductModel product = ProductModel.create(BRAND_ID, "스냅샷상품", money(new BigDecimal("9999")), stock(1));
            ProductSnapshot snapshot = product.snapshotForOrder();
            assertThat(snapshot.productName()).isEqualTo("스냅샷상품");
            assertThat(snapshot.price().value()).isEqualByComparingTo(new BigDecimal("9999"));
            assertThat(snapshot.productId()).isEqualTo(product.getId());
        }
    }

    @DisplayName("삭제 여부 확인 시")
    @Nested
    class IsDeleted {

        @DisplayName("생성 직후에는 삭제되지 않은 상태이다.")
        @Test
        void isDeleted_whenNotDeleted_shouldReturnFalse() {
            ProductModel product = ProductModel.create(BRAND_ID, NAME, money(PRICE), stock(STOCK));
            assertThat(product.isDeleted()).isFalse();
        }

        @DisplayName("delete() 호출 후에는 삭제된 상태이다.")
        @Test
        void isDeleted_afterDelete_shouldReturnTrue() {
            ProductModel product = ProductModel.create(BRAND_ID, NAME, money(PRICE), stock(STOCK));
            product.delete();
            assertThat(product.isDeleted()).isTrue();
        }
    }

    @DisplayName("updateName 시")
    @Nested
    class UpdateName {

        @Test
        void updateName_withValidName_shouldUpdate() {
            ProductModel product = ProductModel.create(BRAND_ID, NAME, money(PRICE), stock(STOCK));
            product.updateName("새 상품명");
            assertThat(product.getName()).isEqualTo("새 상품명");
        }

        @Test
        void updateName_withNull_shouldThrow() {
            ProductModel product = ProductModel.create(BRAND_ID, NAME, money(PRICE), stock(STOCK));
            assertThrows(IllegalArgumentException.class, () -> product.updateName(null));
        }

        @Test
        void updateName_withBlank_shouldThrow() {
            ProductModel product = ProductModel.create(BRAND_ID, NAME, money(PRICE), stock(STOCK));
            assertThrows(IllegalArgumentException.class, () -> product.updateName(""));
        }

        @Test
        void updateName_withWhitespaceOnly_shouldThrow() {
            ProductModel product = ProductModel.create(BRAND_ID, NAME, money(PRICE), stock(STOCK));
            assertThrows(IllegalArgumentException.class, () -> product.updateName("   "));
        }
    }

    @DisplayName("updatePrice 시")
    @Nested
    class UpdatePrice {

        @Test
        void updatePrice_withValidPrice_shouldUpdate() {
            ProductModel product = ProductModel.create(BRAND_ID, NAME, money(PRICE), stock(STOCK));
            product.updatePrice(money(new BigDecimal("20000")));
            assertThat(product.getPrice()).isEqualByComparingTo("20000");
        }

        @Test
        void updatePrice_withNull_shouldThrow() {
            ProductModel product = ProductModel.create(BRAND_ID, NAME, money(PRICE), stock(STOCK));
            assertThrows(IllegalArgumentException.class, () -> product.updatePrice(null));
        }

        @Test
        void updatePrice_withNegative_shouldThrow() {
            ProductModel product = ProductModel.create(BRAND_ID, NAME, money(PRICE), stock(STOCK));
            assertThrows(IllegalArgumentException.class, () -> product.updatePrice(money(new BigDecimal("-1"))));
        }
    }

    @DisplayName("updateStockQuantity 시")
    @Nested
    class UpdateStockQuantity {

        @Test
        void updateStockQuantity_withValidValue_shouldUpdate() {
            ProductModel product = ProductModel.create(BRAND_ID, NAME, money(PRICE), stock(STOCK));
            product.updateStockQuantity(stock(20));
            assertThat(product.getStockQuantity()).isEqualTo(20);
        }

        @Test
        void updateStockQuantity_withNegative_shouldThrow() {
            ProductModel product = ProductModel.create(BRAND_ID, NAME, money(PRICE), stock(STOCK));
            assertThrows(IllegalArgumentException.class, () -> product.updateStockQuantity(StockQuantity.of(-1)));
        }

        @DisplayName("재고 0은 허용된다.")
        @Test
        void updateStockQuantity_withZero_shouldSucceed() {
            ProductModel product = ProductModel.create(BRAND_ID, NAME, money(PRICE), stock(5));
            product.updateStockQuantity(stock(0));
            assertThat(product.getStockQuantity()).isZero();
        }
    }

    @DisplayName("increaseStock 시")
    @Nested
    class IncreaseStock {

        @Test
        void increaseStock_withValidQuantity_shouldAdd() {
            ProductModel product = ProductModel.create(BRAND_ID, NAME, money(PRICE), stock(5));
            product.increaseStock(Quantity.of(3));
            assertThat(product.getStockQuantity()).isEqualTo(8);
        }

        @Test
        void increaseStock_withZero_shouldThrow() {
            ProductModel product = ProductModel.create(BRAND_ID, NAME, money(PRICE), stock(STOCK));
            assertThrows(IllegalArgumentException.class, () -> product.increaseStock(Quantity.of(0)));
        }

        @Test
        void increaseStock_withNegative_shouldThrow() {
            ProductModel product = ProductModel.create(BRAND_ID, NAME, money(PRICE), stock(STOCK));
            assertThrows(IllegalArgumentException.class, () -> product.increaseStock(Quantity.of(-1)));
        }
    }

    @DisplayName("decreaseStock 시")
    @Nested
    class DecreaseStock {

        @Test
        void decreaseStock_withValidQuantity_shouldSubtract() {
            ProductModel product = ProductModel.create(BRAND_ID, NAME, money(PRICE), stock(10));
            product.decreaseStock(Quantity.of(3));
            assertThat(product.getStockQuantity()).isEqualTo(7);
        }

        @Test
        void decreaseStock_toZero_shouldSucceed() {
            ProductModel product = ProductModel.create(BRAND_ID, NAME, money(PRICE), stock(5));
            product.decreaseStock(Quantity.of(5));
            assertThat(product.getStockQuantity()).isZero();
        }

        @Test
        void decreaseStock_whenInsufficient_shouldThrow() {
            ProductModel product = ProductModel.create(BRAND_ID, NAME, money(PRICE), stock(2));
            assertThrows(IllegalArgumentException.class, () -> product.decreaseStock(Quantity.of(3)));
            assertThat(product.getStockQuantity()).isEqualTo(2);
        }

        @Test
        void decreaseStock_withNull_shouldThrow() {
            ProductModel product = ProductModel.create(BRAND_ID, NAME, money(PRICE), stock(STOCK));
            assertThrows(IllegalArgumentException.class, () -> product.decreaseStock(null));
        }
    }
}
