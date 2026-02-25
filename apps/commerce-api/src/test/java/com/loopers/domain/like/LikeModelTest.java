package com.loopers.domain.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LikeModelTest {

    private ProductModel createProduct() {
        BrandModel brand = new BrandModel("나이키", "스포츠 의류 및 신발 브랜드");
        return new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE);
    }

    @DisplayName("좋아요를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("userId와 product가 주어지면, 정상적으로 생성된다.")
        @Test
        void createsLike_whenAllRequiredFieldsAreProvided() {
            // arrange
            ProductModel product = createProduct();

            // act
            LikeModel like = new LikeModel(1L, product);

            // assert
            assertAll(
                () -> assertThat(like.getUserId()).isEqualTo(1L),
                () -> assertThat(like.getProduct()).isEqualTo(product)
            );
        }

        @DisplayName("userId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenUserIdIsNull() {
            // arrange
            ProductModel product = createProduct();

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new LikeModel(null, product);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("product가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenProductIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new LikeModel(1L, null);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
