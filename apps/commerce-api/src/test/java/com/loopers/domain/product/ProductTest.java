package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductTest {

    private static final Long VALID_BRAND_ID = 1L;
    private static final String VALID_NAME = "나이키 에어맥스";
    private static final Money VALID_PRICE = new Money(10000);
    private static final Stock VALID_STOCK = new Stock(100);

    @DisplayName("Money VO를 생성할 때")
    @Nested
    class MoneyCreate {

        @DisplayName("금액이 0 이상이면, 정상적으로 생성된다.")
        @Test
        void createsMoney_whenAmountIsZeroOrPositive() {
            // act
            Money zero = new Money(0);
            Money positive = new Money(50000);

            // assert
            assertThat(zero.getAmount()).isEqualTo(0);
            assertThat(positive.getAmount()).isEqualTo(50000);
        }

        @DisplayName("금액이 음수이면, 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenAmountIsNegative() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new Money(-1));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("Quantity VO를 생성할 때")
    @Nested
    class QuantityCreate {

        @DisplayName("수량이 1 이상이면, 정상적으로 생성된다.")
        @Test
        void createsQuantity_whenValueIsPositive() {
            // act
            Quantity quantity = new Quantity(1);

            // assert
            assertThat(quantity.getValue()).isEqualTo(1);
        }

        @DisplayName("수량이 0 이하이면, 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenValueIsZeroOrNegative() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new Quantity(0));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("Stock VO를 생성할 때")
    @Nested
    class StockCreate {

        @DisplayName("재고가 0 이상이면, 정상적으로 생성된다.")
        @Test
        void createsStock_whenQuantityIsZeroOrPositive() {
            // act
            Stock zero = new Stock(0);
            Stock positive = new Stock(50);

            // assert
            assertThat(zero.getQuantity()).isEqualTo(0);
            assertThat(positive.getQuantity()).isEqualTo(50);
        }

        @DisplayName("재고가 음수이면, 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenQuantityIsNegative() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new Stock(-1));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고를 감소시킬 때, 충분한 재고가 있으면 감소된 재고를 반환한다.")
        @Test
        void decreasesStock_whenStockIsEnough() {
            // arrange
            Stock stock = new Stock(10);
            Quantity quantity = new Quantity(3);

            // act
            Stock decreased = stock.decrease(quantity);

            // assert
            assertThat(decreased.getQuantity()).isEqualTo(7);
        }

        @DisplayName("재고를 감소시킬 때, 재고가 부족하면 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenStockIsInsufficient() {
            // arrange
            Stock stock = new Stock(2);
            Quantity quantity = new Quantity(5);

            // act
            CoreException result = assertThrows(CoreException.class, () -> stock.decrease(quantity));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고를 증가시키면, 증가된 재고를 반환한다.")
        @Test
        void increasesStock_whenCalled() {
            // arrange
            Stock stock = new Stock(5);
            Quantity quantity = new Quantity(3);

            // act
            Stock increased = stock.increase(quantity);

            // assert
            assertThat(increased.getQuantity()).isEqualTo(8);
        }

        @DisplayName("재고가 충분한지 확인할 때, 재고가 충분하면 true를 반환한다.")
        @Test
        void returnsTrue_whenStockIsEnough() {
            // arrange
            Stock stock = new Stock(10);

            // assert - hasEnough 호출과 검증을 한 번에 표현
            assertThat(stock.hasEnough(new Quantity(10))).isTrue();
            assertThat(stock.hasEnough(new Quantity(11))).isFalse();
        }
    }

    @DisplayName("상품을 생성할 때")
    @Nested
    class Create {

        @DisplayName("모든 정보가 올바르면, 상품이 정상적으로 생성된다.")
        @Test
        void createsProduct_whenAllInfoIsValid() {
            // act
            Product product = new Product(VALID_BRAND_ID, VALID_NAME, VALID_PRICE, VALID_STOCK);

            // assert
            assertThat(product.getBrandId()).isEqualTo(VALID_BRAND_ID);
            assertThat(product.getName()).isEqualTo(VALID_NAME);
            assertThat(product.getPrice().getAmount()).isEqualTo(VALID_PRICE.getAmount());
            assertThat(product.getStock().getQuantity()).isEqualTo(VALID_STOCK.getQuantity());
            assertThat(product.getLikeCount()).isEqualTo(0);
        }

        @DisplayName("브랜드 ID가 null이면, 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenBrandIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> new Product(null, VALID_NAME, VALID_PRICE, VALID_STOCK));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품명이 null이면, 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> new Product(VALID_BRAND_ID, null, VALID_PRICE, VALID_STOCK));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품명이 비어있으면, 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> new Product(VALID_BRAND_ID, "  ", VALID_PRICE, VALID_STOCK));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("상품 정보를 수정할 때")
    @Nested
    class Update {

        @DisplayName("올바른 정보로 수정하면, 상품 정보가 변경된다.")
        @Test
        void updatesProduct_whenNewInfoIsValid() {
            // arrange
            Product product = new Product(VALID_BRAND_ID, VALID_NAME, VALID_PRICE, VALID_STOCK);
            Money newPrice = new Money(20000);
            Stock newStock = new Stock(50);

            // act
            product.update("나이키 조던", newPrice, newStock);

            // assert
            assertThat(product.getName()).isEqualTo("나이키 조던");
            assertThat(product.getPrice().getAmount()).isEqualTo(20000);
            assertThat(product.getStock().getQuantity()).isEqualTo(50);
        }

        @DisplayName("상품명이 null이면, 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNewNameIsNull() {
            // arrange
            Product product = new Product(VALID_BRAND_ID, VALID_NAME, VALID_PRICE, VALID_STOCK);

            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> product.update(null, VALID_PRICE, VALID_STOCK));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품명이 비어있으면, 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNewNameIsBlank() {
            // arrange
            Product product = new Product(VALID_BRAND_ID, VALID_NAME, VALID_PRICE, VALID_STOCK);

            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> product.update("  ", VALID_PRICE, VALID_STOCK));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수정 후에도 브랜드 ID는 변경되지 않는다.")
        @Test
        void doesNotChangeBrandId_afterUpdate() {
            // arrange
            Product product = new Product(VALID_BRAND_ID, VALID_NAME, VALID_PRICE, VALID_STOCK);

            // act
            product.update("다른상품", new Money(5000), new Stock(10));

            // assert
            assertThat(product.getBrandId()).isEqualTo(VALID_BRAND_ID);
        }
    }

    @DisplayName("좋아요 수를 변경할 때")
    @Nested
    class LikeCount {

        @DisplayName("좋아요를 증가시키면, likeCount가 1 증가한다.")
        @Test
        void increasesLikeCount_whenCalled() {
            // arrange
            Product product = new Product(VALID_BRAND_ID, VALID_NAME, VALID_PRICE, VALID_STOCK);

            // act
            product.increaseLikeCount();

            // assert
            assertThat(product.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("좋아요를 감소시킬 때, likeCount가 0보다 크면 1 감소한다.")
        @Test
        void decreasesLikeCount_whenLikeCountIsPositive() {
            // arrange
            Product product = new Product(VALID_BRAND_ID, VALID_NAME, VALID_PRICE, VALID_STOCK);
            product.increaseLikeCount();

            // act
            product.decreaseLikeCount();

            // assert
            assertThat(product.getLikeCount()).isEqualTo(0);
        }

        @DisplayName("좋아요를 감소시킬 때, likeCount가 0이면 변경되지 않는다.")
        @Test
        void doesNotDecreaseBelowZero_whenLikeCountIsZero() {
            // arrange
            Product product = new Product(VALID_BRAND_ID, VALID_NAME, VALID_PRICE, VALID_STOCK);

            // act
            product.decreaseLikeCount();

            // assert
            assertThat(product.getLikeCount()).isEqualTo(0);
        }
    }
}
