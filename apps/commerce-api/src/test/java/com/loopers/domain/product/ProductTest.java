package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class ProductTest {

    @DisplayName("상품을 생성할 때")
    @Nested
    class Create {

        @DisplayName("유효한 정보가 주어지면 성공한다")
        @Test
        void success() {
            Long brandId = 1L;
            String name = "에어맥스";
            Long price = 150_000L;
            int stockQuantity = 10;

            Product product = new Product(brandId, name, price, stockQuantity);

            assertAll(
                () -> assertThat(product.getBrandId()).isEqualTo(brandId),
                () -> assertThat(product.getName()).isEqualTo(name),
                () -> assertThat(product.getPrice()).isEqualTo(price),
                () -> assertThat(product.getStockQuantity()).isEqualTo(stockQuantity)
            );
        }

        @DisplayName("상품명이 비어있으면 예외가 발생한다")
        @Test
        void failsWhenNameIsBlank() {
            assertThatThrownBy(() -> new Product(1L, "  ", 1000L, 5))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }

        @DisplayName("가격이 음수이면 예외가 발생한다")
        @Test
        void failsWhenPriceIsNegative() {
            assertThatThrownBy(() -> new Product(1L, "상품", -1L, 5))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고가 음수이면 예외가 발생한다")
        @Test
        void failsWhenStockQuantityIsNegative() {
            assertThatThrownBy(() -> new Product(1L, "상품", 1000L, -1))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 차감할 때")
    @Nested
    class DecreaseStock {

        @DisplayName("재고가 충분하면 정상 차감된다")
        @Test
        void success() {
            Product product = new Product(1L, "상품", 10_000L, 10);

            product.decreaseStock(3);

            assertThat(product.getStockQuantity()).isEqualTo(7);
        }

        @DisplayName("재고와 동일한 수량을 차감하면 0이 된다")
        @Test
        void successWhenExactStock() {
            Product product = new Product(1L, "상품", 10_000L, 5);

            product.decreaseStock(5);

            assertThat(product.getStockQuantity()).isEqualTo(0);
        }

        @DisplayName("재고가 부족하면 INSUFFICIENT_STOCK 예외가 발생한다")
        @Test
        void failsWhenInsufficientStock() {
            Product product = new Product(1L, "상품", 10_000L, 5);

            assertThatThrownBy(() -> product.decreaseStock(10))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.INSUFFICIENT_STOCK);
        }

        @DisplayName("차감 수량이 0이면 예외가 발생한다")
        @Test
        void failsWhenQuantityIsZero() {
            Product product = new Product(1L, "상품", 10_000L, 10);

            assertThatThrownBy(() -> product.decreaseStock(0))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }

        @DisplayName("차감 수량이 음수이면 예외가 발생한다")
        @Test
        void failsWhenQuantityIsNegative() {
            Product product = new Product(1L, "상품", 10_000L, 10);

            assertThatThrownBy(() -> product.decreaseStock(-1))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }
    }
}
