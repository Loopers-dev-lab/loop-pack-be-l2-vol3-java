package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductTest {

    @DisplayName("상품을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 정보이면, 상품이 생성된다.")
        @Test
        void createsProduct_whenValidInfo() {
            Product product = new Product(1L, "나이키 에어맥스", new Money(129000));

            assertAll(
                () -> assertThat(product.getBrandId()).isEqualTo(1L),
                () -> assertThat(product.getName()).isEqualTo("나이키 에어맥스"),
                () -> assertThat(product.getPrice()).isEqualTo(new Money(129000)),
                () -> assertThat(product.getLikeCount()).isEqualTo(0)
            );
        }

        @DisplayName("brandId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBrandIdIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new Product(null, "나이키 에어맥스", new Money(129000)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            CoreException result = assertThrows(CoreException.class,
                () -> new Product(1L, "", new Money(129000)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new Product(1L, "나이키 에어맥스", null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("상품 정보를 변경할 때, ")
    @Nested
    class ChangeDetails {

        @DisplayName("올바른 정보이면, 이름/가격이 수정된다.")
        @Test
        void changesDetails_whenValidInfo() {
            Product product = new Product(1L, "나이키 에어맥스", new Money(129000));
            product.changeDetails("아디다스 울트라부스트", new Money(159000));

            assertAll(
                () -> assertThat(product.getName()).isEqualTo("아디다스 울트라부스트"),
                () -> assertThat(product.getPrice()).isEqualTo(new Money(159000))
            );
        }

        @DisplayName("brandId는 변경되지 않는다.")
        @Test
        void doesNotChangeBrandId() {
            Product product = new Product(1L, "나이키 에어맥스", new Money(129000));
            product.changeDetails("아디다스 울트라부스트", new Money(159000));

            assertThat(product.getBrandId()).isEqualTo(1L);
        }
    }

    @DisplayName("좋아요 수를 증가시킬 때, ")
    @Nested
    class IncrementLikeCount {

        @DisplayName("좋아요 수가 1 증가한다.")
        @Test
        void incrementsLikeCount() {
            Product product = new Product(1L, "나이키 에어맥스", new Money(129000));

            product.incrementLikeCount();

            assertThat(product.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("여러 번 호출하면, 호출 횟수만큼 증가한다.")
        @Test
        void incrementsMultipleTimes() {
            Product product = new Product(1L, "나이키 에어맥스", new Money(129000));

            product.incrementLikeCount();
            product.incrementLikeCount();
            product.incrementLikeCount();

            assertThat(product.getLikeCount()).isEqualTo(3);
        }
    }

    @DisplayName("좋아요 수를 감소시킬 때, ")
    @Nested
    class DecrementLikeCount {

        @DisplayName("좋아요 수가 1보다 크면, 1 감소한다.")
        @Test
        void decrementsLikeCount() {
            Product product = new Product(1L, "나이키 에어맥스", new Money(129000));
            product.incrementLikeCount();
            product.incrementLikeCount();

            product.decrementLikeCount();

            assertThat(product.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("좋아요 수가 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLikeCountIsZero() {
            Product product = new Product(1L, "나이키 에어맥스", new Money(129000));

            CoreException result = assertThrows(CoreException.class, product::decrementLikeCount);
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
