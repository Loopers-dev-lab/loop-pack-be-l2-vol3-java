package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductModelTest {

    private BrandModel createBrand() {
        return new BrandModel("나이키", "스포츠 의류 및 신발 브랜드");
    }

    @DisplayName("상품을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("모든 필수 필드가 주어지면, 정상적으로 생성된다.")
        @Test
        void createsProduct_whenAllRequiredFieldsAreProvided() {
            // arrange
            BrandModel brand = createBrand();

            // act
            ProductModel product = new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE);

            // assert
            assertAll(
                () -> assertThat(product.getBrand()).isEqualTo(brand),
                () -> assertThat(product.getName()).isEqualTo("에어맥스"),
                () -> assertThat(product.getPrice()).isEqualTo(150000L),
                () -> assertThat(product.getDescription()).isEqualTo("나이키 에어맥스"),
                () -> assertThat(product.getStockQuantity()).isEqualTo(100),
                () -> assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE)
            );
        }

        @DisplayName("description이 null이면, 정상적으로 생성된다.")
        @Test
        void createsProduct_whenDescriptionIsNull() {
            // arrange
            BrandModel brand = createBrand();

            // act
            ProductModel product = new ProductModel(brand, "에어맥스", 150000L, null, 100, ProductStatus.ON_SALE);

            // assert
            assertThat(product.getDescription()).isNull();
        }

        @DisplayName("brand가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenBrandIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new ProductModel(null, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("name이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsBlank() {
            // arrange
            BrandModel brand = createBrand();

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new ProductModel(brand, "   ", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("price가 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPriceIsNegative() {
            // arrange
            BrandModel brand = createBrand();

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new ProductModel(brand, "에어맥스", -1L, "나이키 에어맥스", 100, ProductStatus.ON_SALE);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("stockQuantity가 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenStockQuantityIsNegative() {
            // arrange
            BrandModel brand = createBrand();

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", -1, ProductStatus.ON_SALE);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("status가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenStatusIsNull() {
            // arrange
            BrandModel brand = createBrand();

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, null);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("상품을 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("정상적인 정보가 주어지면, 상품이 수정된다.")
        @Test
        void updatesProduct_whenValidFieldsAreProvided() {
            // arrange
            BrandModel brand = createBrand();
            BrandModel newBrand = new BrandModel("아디다스", "독일 스포츠 브랜드");
            ProductModel product = new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE);

            // act
            product.update(newBrand, "울트라부스트", 200000L, "아디다스 울트라부스트", 50, ProductStatus.ON_SALE);

            // assert
            assertAll(
                () -> assertThat(product.getBrand()).isEqualTo(newBrand),
                () -> assertThat(product.getName()).isEqualTo("울트라부스트"),
                () -> assertThat(product.getPrice()).isEqualTo(200000L),
                () -> assertThat(product.getDescription()).isEqualTo("아디다스 울트라부스트"),
                () -> assertThat(product.getStockQuantity()).isEqualTo(50),
                () -> assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE)
            );
        }

        @DisplayName("name이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsBlank() {
            // arrange
            BrandModel brand = createBrand();
            ProductModel product = new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                product.update(brand, "   ", 200000L, "설명", 50, ProductStatus.ON_SALE);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 차감할 때, ")
    @Nested
    class DeductStock {

        @DisplayName("충분한 재고가 있으면, 재고가 차감된다.")
        @Test
        void deductsStock_whenSufficientStockExists() {
            // arrange
            BrandModel brand = createBrand();
            ProductModel product = new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE);

            // act
            product.deductStock(30);

            // assert
            assertThat(product.getStockQuantity()).isEqualTo(70);
        }

        @DisplayName("재고가 부족하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenInsufficientStock() {
            // arrange
            BrandModel brand = createBrand();
            ProductModel product = new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 10, ProductStatus.ON_SALE);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                product.deductStock(11);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("차감 수량이 0 이하이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenQuantityIsZeroOrNegative() {
            // arrange
            BrandModel brand = createBrand();
            ProductModel product = new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                product.deductStock(0);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("상품을 삭제할 때, ")
    @Nested
    class Delete {

        @DisplayName("삭제하면, deletedAt이 설정된다.")
        @Test
        void setsDeletedAt_whenDeleted() {
            // arrange
            BrandModel brand = createBrand();
            ProductModel product = new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE);

            // act
            product.delete();

            // assert
            assertThat(product.getDeletedAt()).isNotNull();
        }

        @DisplayName("이미 삭제된 상품을 다시 삭제해도, 멱등하게 동작한다.")
        @Test
        void isIdempotent_whenDeletedTwice() {
            // arrange
            BrandModel brand = createBrand();
            ProductModel product = new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE);
            product.delete();
            var firstDeletedAt = product.getDeletedAt();

            // act
            product.delete();

            // assert
            assertThat(product.getDeletedAt()).isEqualTo(firstDeletedAt);
        }
    }
}
