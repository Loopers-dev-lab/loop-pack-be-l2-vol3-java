package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductModelTest {

    @DisplayName("상품 생성")
    @Nested
    class Create {

        @DisplayName("이름, 설명, 가격, 브랜드ID가 주어지면 정상 생성된다")
        @Test
        void createsSuccessfully() {
            // arrange
            String name = "에어맥스";
            String description = "러닝화";
            Money price = new Money(129000);
            Long brandId = 1L;
            // act
            ProductModel product = new ProductModel(name, description, price, brandId);
            // assert
            assertAll(
                () -> assertThat(product.getName()).isEqualTo(name),
                () -> assertThat(product.getDescription()).isEqualTo(description),
                () -> assertThat(product.getPrice().value()).isEqualTo(129000),
                () -> assertThat(product.getBrandId()).isEqualTo(brandId),
                () -> assertThat(product.getLikeCount()).isEqualTo(0)
            );
        }

        @DisplayName("이름이 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsOnNullName() {
            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                new ProductModel(null, "설명", new Money(10000), 1L);
            });
            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 공백이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsOnBlankName() {
            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                new ProductModel("  ", "설명", new Money(10000), 1L);
            });
            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsOnNullPrice() {
            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                new ProductModel("상품", "설명", null, 1L);
            });
            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("상품 수정")
    @Nested
    class Update {

        @DisplayName("이름, 설명, 가격을 수정할 수 있다")
        @Test
        void updatesSuccessfully() {
            // arrange
            ProductModel product = new ProductModel("에어맥스", "러닝화", new Money(129000), 1L);
            // act
            product.update("에어포스", "캐주얼", new Money(109000));
            // assert
            assertAll(
                () -> assertThat(product.getName()).isEqualTo("에어포스"),
                () -> assertThat(product.getDescription()).isEqualTo("캐주얼"),
                () -> assertThat(product.getPrice().value()).isEqualTo(109000)
            );
        }

        @DisplayName("수정 시 이름이 공백이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsOnBlankNameUpdate() {
            // arrange
            ProductModel product = new ProductModel("에어맥스", "러닝화", new Money(129000), 1L);
            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                product.update("  ", "설명", new Money(10000));
            });
            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수정 시 가격이 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsOnNullPriceUpdate() {
            // arrange
            ProductModel product = new ProductModel("에어맥스", "러닝화", new Money(129000), 1L);
            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                product.update("에어포스", "설명", null);
            });
            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("likeCount")
    @Nested
    class LikeCount {

        @DisplayName("좋아요 수를 증가시킨다")
        @Test
        void increasesLikeCount() {
            // arrange
            ProductModel product = new ProductModel("에어맥스", "러닝화", new Money(129000), 1L);
            // act
            product.increaseLikeCount();
            // assert
            assertThat(product.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("좋아요 수를 감소시킨다")
        @Test
        void decreasesLikeCount() {
            // arrange
            ProductModel product = new ProductModel("에어맥스", "러닝화", new Money(129000), 1L);
            product.increaseLikeCount();
            product.increaseLikeCount();
            // act
            product.decreaseLikeCount();
            // assert
            assertThat(product.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("좋아요 수가 0 미만으로 감소하지 않는다")
        @Test
        void doesNotDecreaseBelowZero() {
            // arrange
            ProductModel product = new ProductModel("에어맥스", "러닝화", new Money(129000), 1L);
            // act
            product.decreaseLikeCount();
            // assert
            assertThat(product.getLikeCount()).isEqualTo(0);
        }

        @DisplayName("incrementLikeCount()를 호출하면 likeCount가 1 증가한다")
        @Test
        void incrementsLikeCount() {
            // given
            ProductModel product = new ProductModel("에어맥스 90", "러닝화", new Money(129000), 1L);

            // when
            product.incrementLikeCount();

            // then
            assertThat(product.likeCount()).isEqualTo(1);
        }

        @DisplayName("decrementLikeCount()를 호출하면 likeCount가 1 감소한다")
        @Test
        void decrementsLikeCount() {
            // given
            ProductModel product = new ProductModel("에어맥스 90", "러닝화", new Money(129000), 1L);
            product.incrementLikeCount();
            product.incrementLikeCount();

            // when
            product.decrementLikeCount();

            // then
            assertThat(product.likeCount()).isEqualTo(1);
        }

        @DisplayName("likeCount가 0일 때 decrementLikeCount()를 호출하면 0을 유지한다")
        @Test
        void doesNotGoBelowZero() {
            // given
            ProductModel product = new ProductModel("에어맥스 90", "러닝화", new Money(129000), 1L);

            // when
            product.decrementLikeCount();

            // then
            assertThat(product.likeCount()).isEqualTo(0);
        }
    }
}
