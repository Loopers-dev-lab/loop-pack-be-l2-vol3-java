package com.loopers.domain.product;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductTest {

    @DisplayName("Product를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 정보로 생성하면, 정상적으로 생성된다.")
        @Test
        void createsProduct_whenInfoIsValid() {
            // arrange & act
            Product product = Product.create(
                1L,
                "Air Max 90",
                "클래식 스니커즈",
                new Money(150000),
                new Stock(100),
                "https://example.com/airmax.png"
            );

            // assert
            assertThat(product.getBrandId()).isEqualTo(1L);
            assertThat(product.getName()).isEqualTo("Air Max 90");
            assertThat(product.getDescription()).isEqualTo("클래식 스니커즈");
            assertThat(product.getPrice().amount()).isEqualTo(150000);
            assertThat(product.getStock().quantity()).isEqualTo(100);
            assertThat(product.getImageUrl()).isEqualTo("https://example.com/airmax.png");
            assertThat(product.getCreatedAt()).isNotNull();
            assertThat(product.isDeleted()).isFalse();
        }

        @DisplayName("ID는 null로 생성된다.")
        @Test
        void createsWithNullId() {
            // act
            Product product = Product.create(1L, "상품", "설명", new Money(10000), new Stock(10), "url");

            // assert
            assertThat(product.getId()).isNull();
        }
    }

    @DisplayName("Product를 복원할 때,")
    @Nested
    class Reconstitute {

        @DisplayName("모든 필드가 복원된다.")
        @Test
        void reconstitutesAllFields() {
            // arrange
            Long id = 1L;
            Long brandId = 10L;
            String name = "Air Max 90";
            String description = "클래식 스니커즈";
            Money price = new Money(150000);
            Stock stock = new Stock(100);
            String imageUrl = "https://example.com/airmax.png";
            ZonedDateTime createdAt = ZonedDateTime.now().minusDays(1);
            ZonedDateTime updatedAt = ZonedDateTime.now();
            ZonedDateTime deletedAt = null;

            // act
            Product product = Product.reconstitute(
                id, brandId, name, description, price, stock, imageUrl, createdAt, updatedAt, deletedAt
            );

            // assert
            assertThat(product.getId()).isEqualTo(id);
            assertThat(product.getBrandId()).isEqualTo(brandId);
            assertThat(product.getName()).isEqualTo(name);
            assertThat(product.getDescription()).isEqualTo(description);
            assertThat(product.getPrice()).isEqualTo(price);
            assertThat(product.getStock()).isEqualTo(stock);
            assertThat(product.getImageUrl()).isEqualTo(imageUrl);
            assertThat(product.getCreatedAt()).isEqualTo(createdAt);
            assertThat(product.getUpdatedAt()).isEqualTo(updatedAt);
            assertThat(product.getDeletedAt()).isNull();
        }
    }

    @DisplayName("재고를 차감할 때,")
    @Nested
    class DecreaseStock {

        @DisplayName("충분한 재고가 있으면, 재고가 차감된다.")
        @Test
        void decreasesStock_whenStockIsSufficient() {
            // arrange
            Product product = Product.create(1L, "상품", "설명", new Money(10000), new Stock(100), "url");

            // act
            product.decreaseStock(30);

            // assert
            assertThat(product.getStock().quantity()).isEqualTo(70);
        }

        @DisplayName("재고와 정확히 같은 수량을 차감하면, 재고가 0이 된다.")
        @Test
        void decreasesStockToZero_whenDecreasingExactAmount() {
            // arrange
            Product product = Product.create(1L, "상품", "설명", new Money(10000), new Stock(50), "url");

            // act
            product.decreaseStock(50);

            // assert
            assertThat(product.getStock().quantity()).isEqualTo(0);
        }

        @DisplayName("재고가 부족하면, INSUFFICIENT_STOCK 예외가 발생한다.")
        @Test
        void throwsInsufficientStockException_whenStockIsInsufficient() {
            // arrange
            Product product = Product.create(1L, "상품", "설명", new Money(10000), new Stock(10), "url");

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                product.decreaseStock(20);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.INSUFFICIENT_STOCK);
        }

        @DisplayName("차감 수량이 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenQuantityIsZero() {
            // arrange
            Product product = Product.create(1L, "상품", "설명", new Money(10000), new Stock(100), "url");

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                product.decreaseStock(0);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("차감 수량이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenQuantityIsNegative() {
            // arrange
            Product product = Product.create(1L, "상품", "설명", new Money(10000), new Stock(100), "url");

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                product.decreaseStock(-10);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("삭제된 상품의 재고를 차감하면, PRODUCT_DELETED 예외가 발생한다.")
        @Test
        void throwsProductDeletedException_whenProductIsDeleted() {
            // arrange
            Product product = Product.create(1L, "상품", "설명", new Money(10000), new Stock(100), "url");
            product.delete();

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                product.decreaseStock(10);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.PRODUCT_DELETED);
        }
    }

    @DisplayName("Product를 수정할 때,")
    @Nested
    class Update {

        @DisplayName("활성 상품은 정상적으로 수정된다.")
        @Test
        void updatesProduct_whenProductIsActive() {
            // arrange
            Product product = Product.create(1L, "상품", "설명", new Money(10000), new Stock(100), "url");

            // act
            product.update("수정된 상품", "수정된 설명", new Money(20000), new Stock(50), "new_url");

            // assert
            assertThat(product.getName()).isEqualTo("수정된 상품");
            assertThat(product.getDescription()).isEqualTo("수정된 설명");
            assertThat(product.getPrice().amount()).isEqualTo(20000);
            assertThat(product.getStock().quantity()).isEqualTo(50);
            assertThat(product.getImageUrl()).isEqualTo("new_url");
        }

        @DisplayName("삭제된 상품은 수정할 수 없다.")
        @Test
        void throwsException_whenProductIsDeleted() {
            // arrange
            Product product = Product.create(1L, "상품", "설명", new Money(10000), new Stock(100), "url");
            product.delete();

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                product.update("수정", "설명", new Money(20000), new Stock(50), "url");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.PRODUCT_DELETED);
        }
    }

    @DisplayName("Product를 삭제할 때,")
    @Nested
    class Delete {

        @DisplayName("활성 상품은 삭제된다.")
        @Test
        void deletesProduct_whenProductIsActive() {
            // arrange
            Product product = Product.create(1L, "상품", "설명", new Money(10000), new Stock(100), "url");

            // act
            product.delete();

            // assert
            assertThat(product.isDeleted()).isTrue();
            assertThat(product.getDeletedAt()).isNotNull();
        }

        @DisplayName("이미 삭제된 상품을 삭제해도 멱등하게 동작한다.")
        @Test
        void isIdempotent_whenProductIsAlreadyDeleted() {
            // arrange
            Product product = Product.create(1L, "상품", "설명", new Money(10000), new Stock(100), "url");
            product.delete();
            ZonedDateTime firstDeletedAt = product.getDeletedAt();

            // act
            assertDoesNotThrow(() -> product.delete());

            // assert
            assertThat(product.getDeletedAt()).isEqualTo(firstDeletedAt);
        }
    }
}
