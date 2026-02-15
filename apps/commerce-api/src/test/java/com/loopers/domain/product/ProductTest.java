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
            Product product = new Product(1L, "나이키 에어맥스", new Money(129000), new Stock(100));

            assertAll(
                () -> assertThat(product.getBrandId()).isEqualTo(1L),
                () -> assertThat(product.getName()).isEqualTo("나이키 에어맥스"),
                () -> assertThat(product.getPrice()).isEqualTo(new Money(129000)),
                () -> assertThat(product.getStock()).isEqualTo(new Stock(100)),
                () -> assertThat(product.getLikeCount()).isEqualTo(0)
            );
        }

        @DisplayName("brandId가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBrandIdIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new Product(null, "나이키 에어맥스", new Money(129000), new Stock(100)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            CoreException result = assertThrows(CoreException.class,
                () -> new Product(1L, "", new Money(129000), new Stock(100)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPriceIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new Product(1L, "나이키 에어맥스", null, new Stock(100)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new Product(1L, "나이키 에어맥스", new Money(129000), null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("상품을 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("올바른 정보이면, 이름/가격/재고가 수정된다.")
        @Test
        void updatesProduct_whenValidInfo() {
            Product product = new Product(1L, "나이키 에어맥스", new Money(129000), new Stock(100));
            product.update("아디다스 울트라부스트", new Money(159000), new Stock(50));

            assertAll(
                () -> assertThat(product.getName()).isEqualTo("아디다스 울트라부스트"),
                () -> assertThat(product.getPrice()).isEqualTo(new Money(159000)),
                () -> assertThat(product.getStock()).isEqualTo(new Stock(50))
            );
        }

        @DisplayName("brandId는 변경되지 않는다.")
        @Test
        void doesNotChangeBrandId() {
            Product product = new Product(1L, "나이키 에어맥스", new Money(129000), new Stock(100));
            product.update("아디다스 울트라부스트", new Money(159000), new Stock(50));

            assertThat(product.getBrandId()).isEqualTo(1L);
        }
    }

    @DisplayName("재고를 차감할 때, ")
    @Nested
    class DeductStock {

        @DisplayName("충분한 재고가 있으면, 재고가 차감된다.")
        @Test
        void deductsStock_whenSufficient() {
            Product product = new Product(1L, "나이키 에어맥스", new Money(129000), new Stock(10));
            product.deductStock(3);

            assertThat(product.getStock()).isEqualTo(new Stock(7));
        }

        @DisplayName("재고가 부족하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenInsufficient() {
            Product product = new Product(1L, "나이키 에어맥스", new Money(129000), new Stock(2));

            CoreException result = assertThrows(CoreException.class, () -> product.deductStock(3));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("좋아요 수를 변경할 때, ")
    @Nested
    class LikeCount {

        @DisplayName("좋아요를 추가하면, 1 증가한다.")
        @Test
        void incrementsLikeCount() {
            Product product = new Product(1L, "나이키 에어맥스", new Money(129000), new Stock(10));
            product.addLikeCount();

            assertThat(product.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("좋아요를 취소하면, 1 감소한다.")
        @Test
        void decrementsLikeCount() {
            Product product = new Product(1L, "나이키 에어맥스", new Money(129000), new Stock(10));
            product.addLikeCount();
            product.subtractLikeCount();

            assertThat(product.getLikeCount()).isEqualTo(0);
        }

        @DisplayName("좋아요가 0일 때 취소하면, 0 이하로 내려가지 않는다.")
        @Test
        void doesNotGoBelowZero() {
            Product product = new Product(1L, "나이키 에어맥스", new Money(129000), new Stock(10));
            product.subtractLikeCount();

            assertThat(product.getLikeCount()).isEqualTo(0);
        }
    }
}
