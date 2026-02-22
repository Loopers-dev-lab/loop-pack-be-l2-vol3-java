package com.loopers.domain.product;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Product 도메인 테스트")
class ProductTest {

    @Nested
    @DisplayName("생성 테스트")
    class CreateTest {

        @Test
        @DisplayName("유효한 정보로 상품을 생성할 수 있다")
        void createProduct() {
            Product product = Product.create(1L, "테스트 상품", Money.of(10000L), 100);

            assertThat(product.getId()).isNull();
            assertThat(product.getBrandId()).isEqualTo(1L);
            assertThat(product.getName()).isEqualTo("테스트 상품");
            assertThat(product.getStock()).isEqualTo(100);
        }

        @Test
        @DisplayName("상품명이 빈 값이면 예외가 발생한다")
        void createWithEmptyNameThrowsException() {
            assertThatThrownBy(() -> Product.create(1L, "", Money.of(10000L), 100))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("재고가 음수이면 예외가 발생한다")
        void createWithNegativeStockThrowsException() {
            assertThatThrownBy(() -> Product.create(1L, "테스트 상품", Money.of(10000L), -1))
                    .isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("재고 차감 테스트")
    class DecreaseStockTest {

        @Test
        @DisplayName("재고 차감에 성공한다")
        void decreaseStockSuccess() {
            Product product = Product.create(1L, "테스트 상품", Money.of(10000L), 100);

            product.decreaseStock(30);

            assertThat(product.getStock()).isEqualTo(70);
        }

        @Test
        @DisplayName("재고를 0까지 차감할 수 있다")
        void decreaseStockToZero() {
            Product product = Product.create(1L, "테스트 상품", Money.of(10000L), 50);

            product.decreaseStock(50);

            assertThat(product.getStock()).isZero();
        }

        @Test
        @DisplayName("재고보다 많은 수량을 차감하면 예외가 발생한다")
        void decreaseStockInsufficientThrowsException() {
            Product product = Product.create(1L, "테스트 상품", Money.of(10000L), 10);

            assertThatThrownBy(() -> product.decreaseStock(15))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("재고가 부족합니다");
        }

        @Test
        @DisplayName("0 이하의 수량을 차감하면 예외가 발생한다")
        void decreaseStockZeroOrNegativeThrowsException() {
            Product product = Product.create(1L, "테스트 상품", Money.of(10000L), 100);

            assertThatThrownBy(() -> product.decreaseStock(0))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("차감 수량은 1 이상");

            assertThatThrownBy(() -> product.decreaseStock(-5))
                    .isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("재고 증가 테스트")
    class IncreaseStockTest {

        @Test
        @DisplayName("재고 증가에 성공한다")
        void increaseStockSuccess() {
            Product product = Product.create(1L, "테스트 상품", Money.of(10000L), 100);

            product.increaseStock(50);

            assertThat(product.getStock()).isEqualTo(150);
        }

        @Test
        @DisplayName("0 이하의 수량을 증가하면 예외가 발생한다")
        void increaseStockZeroOrNegativeThrowsException() {
            Product product = Product.create(1L, "테스트 상품", Money.of(10000L), 100);

            assertThatThrownBy(() -> product.increaseStock(0))
                    .isInstanceOf(CoreException.class);
        }
    }
}
