package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ProductModelTest {

    @DisplayName("상품을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("모든 정보가 유효하면, 정상적으로 생성된다.")
        @Test
        void createProduct() {
            // given
            Long brandId = 1L;
            String name = "에어맥스";
            String description = "러닝화";
            int price = 129000;
            int stockQuantity = 100;
            String imageUrl = "https://example.com/airmax.png";

            // when
            ProductModel product = new ProductModel(brandId, name, description, price, stockQuantity, imageUrl);

            // then
            assertAll(
                    () -> assertThat(product.getBrandId()).isEqualTo(brandId),
                    () -> assertThat(product.getName()).isEqualTo(name),
                    () -> assertThat(product.getDescription()).isEqualTo(description),
                    () -> assertThat(product.getPrice()).isEqualTo(price),
                    () -> assertThat(product.getStockQuantity()).isEqualTo(stockQuantity),
                    () -> assertThat(product.getImageUrl()).isEqualTo(imageUrl)
            );
        }

        @DisplayName("브랜드 ID가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void failWithNullBrandId() {
            // given & when
            CoreException result = assertThrows(CoreException.class, () ->
                    new ProductModel(null, "에어맥스", "러닝화", 129000, 100, "url")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품명이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void failWithNullName() {
            // given & when
            CoreException result = assertThrows(CoreException.class, () ->
                    new ProductModel(1L, null, "러닝화", 129000, 100, "url")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품명이 빈값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void failWithEmptyName() {
            // given & when
            CoreException result = assertThrows(CoreException.class, () ->
                    new ProductModel(1L, " ", "러닝화", 129000, 100, "url")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void failWithNegativePrice() {
            // given & when
            CoreException result = assertThrows(CoreException.class, () ->
                    new ProductModel(1L, "에어맥스", "러닝화", -1, 100, "url")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고가 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void failWithNegativeStock() {
            // given & when
            CoreException result = assertThrows(CoreException.class, () ->
                    new ProductModel(1L, "에어맥스", "러닝화", 129000, -1, "url")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 차감할 때,")
    @Nested
    class DecreaseStock {

        @DisplayName("충분한 재고가 있으면, 정상적으로 차감된다.")
        @Test
        void decreaseStockSuccess() {
            // given
            ProductModel product = new ProductModel(1L, "에어맥스", "러닝화", 129000, 100, "url");

            // when
            product.decreaseStock(10);

            // then
            assertThat(product.getStockQuantity()).isEqualTo(90);
        }

        @DisplayName("재고와 동일한 수량을 차감하면, 재고가 0이 된다.")
        @Test
        void decreaseStockToZero() {
            // given
            ProductModel product = new ProductModel(1L, "에어맥스", "러닝화", 129000, 100, "url");

            // when
            product.decreaseStock(100);

            // then
            assertThat(product.getStockQuantity()).isEqualTo(0);
        }

        @DisplayName("재고가 부족하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void failWithInsufficientStock() {
            // given
            ProductModel product = new ProductModel(1L, "에어맥스", "러닝화", 129000, 5, "url");

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    product.decreaseStock(10)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("차감 수량이 0 이하이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void failWithZeroQuantity() {
            // given
            ProductModel product = new ProductModel(1L, "에어맥스", "러닝화", 129000, 100, null);

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    product.decreaseStock(0)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 증가시킬 때,")
    @Nested
    class IncreaseStock {

        @DisplayName("양수 수량이면, 정상적으로 증가된다.")
        @Test
        void increaseStockSuccess() {
            // given
            ProductModel product = new ProductModel(1L, "에어맥스", "러닝화", 129000, 100, null);

            // when
            product.increaseStock(50);

            // then
            assertThat(product.getStockQuantity()).isEqualTo(150);
        }

        @DisplayName("증가 수량이 0 이하이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void failWithZeroQuantity() {
            // given
            ProductModel product = new ProductModel(1L, "에어맥스", "러닝화", 129000, 100, null);

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    product.increaseStock(0)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("좋아요 수를 증가시킬 때,")
    @Nested
    class IncreaseLikeCount {

        @DisplayName("likeCount가 1 증가한다.")
        @Test
        void increaseLikeCount() {
            // given
            ProductModel product = new ProductModel(1L, "에어맥스", "러닝화", 129000, 100, "url");

            // when
            product.increaseLikeCount();

            // then
            assertThat(product.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("여러 번 호출하면 호출 횟수만큼 증가한다.")
        @Test
        void increaseLikeCountMultipleTimes() {
            // given
            ProductModel product = new ProductModel(1L, "에어맥스", "러닝화", 129000, 100, "url");

            // when
            product.increaseLikeCount();
            product.increaseLikeCount();
            product.increaseLikeCount();

            // then
            assertThat(product.getLikeCount()).isEqualTo(3);
        }
    }

    @DisplayName("좋아요 수를 감소시킬 때,")
    @Nested
    class DecreaseLikeCount {

        @DisplayName("likeCount가 1 감소한다.")
        @Test
        void decreaseLikeCount() {
            // given
            ProductModel product = new ProductModel(1L, "에어맥스", "러닝화", 129000, 100, "url");
            product.increaseLikeCount();  // likeCount = 1

            // when
            product.decreaseLikeCount();

            // then
            assertThat(product.getLikeCount()).isEqualTo(0);
        }

        @DisplayName("likeCount가 0이면, 0 미만으로 내려가지 않는다.")
        @Test
        void decreaseLikeCountNotBelowZero() {
            // given
            ProductModel product = new ProductModel(1L, "에어맥스", "러닝화", 129000, 100, "url");
            // likeCount = 0 (기본값)

            // when
            product.decreaseLikeCount();

            // then
            assertThat(product.getLikeCount()).isEqualTo(0);
        }
    }
}
