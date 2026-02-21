package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ProductErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ProductTest {

    @DisplayName("생성할 때,")
    @Nested
    class 생성 {

        @Test
        void 유효한_정보면_ACTIVE_상태와_likeCount_0으로_생성된다() {
            // act
            Product product = Product.create(1L, "에어맥스", "나이키 에어맥스", 150000);

            // assert
            assertThat(product)
                    .extracting(Product::getBrandId, Product::getName, Product::getDescription,
                            Product::getBasePrice, Product::getStatus, Product::getLikeCount)
                    .containsExactly(1L, "에어맥스", "나이키 에어맥스", 150000, ProductStatus.ACTIVE, 0);
        }
    }

    @DisplayName("수정할 때,")
    @Nested
    class 수정 {

        @Test
        void name_description_basePrice_status가_변경된다() {
            // arrange
            Product product = Product.create(1L, "에어맥스", "나이키 에어맥스", 150000);

            // act
            product.update("에어포스", "나이키 에어포스", 120000, ProductStatus.SOLDOUT);

            // assert
            assertThat(product)
                    .extracting(Product::getName, Product::getDescription, Product::getBasePrice, Product::getStatus)
                    .containsExactly("에어포스", "나이키 에어포스", 120000, ProductStatus.SOLDOUT);
        }
    }

    @DisplayName("삭제할 때,")
    @Nested
    class 삭제 {

        @Test
        void 이미_삭제된_상품을_재삭제하면_예외가_발생한다() {
            // arrange
            Product product = Product.create(1L, "에어맥스", "나이키 에어맥스", 150000);
            product.delete();

            // act & assert
            assertThatThrownBy(product::delete)
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ProductErrorType.ALREADY_DELETED);
        }

        @Test
        void 삭제되지_않은_상품은_정상적으로_삭제된다() {
            // arrange
            Product product = Product.create(1L, "에어맥스", "나이키 에어맥스", 150000);

            // act
            product.delete();

            // assert
            assertThat(product.getDeletedAt()).isNotNull();
        }
    }

    @DisplayName("고객 노출 여부를 확인할 때,")
    @Nested
    class 고객노출여부 {

        @Test
        void HIDDEN_상태이면_노출되지_않는다() {
            // arrange
            Product product = Product.create(1L, "에어맥스", "나이키 에어맥스", 150000);
            product.update("에어맥스", "나이키 에어맥스", 150000, ProductStatus.HIDDEN);

            // act & assert
            assertThat(product.isDisplayable()).isFalse();
        }

        @Test
        void DISCONTINUED_상태이면_노출되지_않는다() {
            // arrange
            Product product = Product.create(1L, "에어맥스", "나이키 에어맥스", 150000);
            product.update("에어맥스", "나이키 에어맥스", 150000, ProductStatus.DISCONTINUED);

            // act & assert
            assertThat(product.isDisplayable()).isFalse();
        }

        @Test
        void ACTIVE_상태이면_노출된다() {
            // arrange
            Product product = Product.create(1L, "에어맥스", "나이키 에어맥스", 150000);

            // act & assert
            assertThat(product.isDisplayable()).isTrue();
        }

        @Test
        void SOLDOUT_상태이면_노출된다() {
            // arrange
            Product product = Product.create(1L, "에어맥스", "나이키 에어맥스", 150000);
            product.update("에어맥스", "나이키 에어맥스", 150000, ProductStatus.SOLDOUT);

            // act & assert
            assertThat(product.isDisplayable()).isTrue();
        }
    }

    @DisplayName("좋아요 수를 변경할 때,")
    @Nested
    class 좋아요수변경 {

        @Test
        void likeCount가_0일_때_decrementLikeCount를_해도_0이다() {
            // arrange
            Product product = Product.create(1L, "에어맥스", "나이키 에어맥스", 150000);

            // act
            product.decrementLikeCount();

            // assert
            assertThat(product.getLikeCount()).isEqualTo(0);
        }

        @Test
        void incrementLikeCount로_likeCount가_증가한다() {
            // arrange
            Product product = Product.create(1L, "에어맥스", "나이키 에어맥스", 150000);

            // act
            product.incrementLikeCount();

            // assert
            assertThat(product.getLikeCount()).isEqualTo(1);
        }

        @Test
        void likeCount가_1_이상일_때_decrementLikeCount로_감소한다() {
            // arrange
            Product product = Product.create(1L, "에어맥스", "나이키 에어맥스", 150000);
            product.incrementLikeCount();
            product.incrementLikeCount();

            // act
            product.decrementLikeCount();

            // assert
            assertThat(product.getLikeCount()).isEqualTo(1);
        }
    }
}
