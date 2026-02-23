package com.loopers.domain.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class StockTest {

    @DisplayName("Stock을 생성할 때,")
    @Nested
    class Constructor {

        @DisplayName("유효한 값이면, 정상 생성된다.")
        @ParameterizedTest(name = "값이 {0}인 재고")
        @ValueSource(longs = {1L, 50L})
        void success(Long value) {
            // act
            var stock = Stock.init(value);

            // assert
            assertThat(stock).isEqualTo(new Stock(value));
        }

        @DisplayName("값이 null이면, REQUIRED_PRODUCT_STOCK 에러가 발생한다.")
        @ParameterizedTest
        @NullSource
        void throwsException_whenNull(Long value) {
            assertThatThrownBy(() -> Stock.init(value))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining(ErrorType.REQUIRED_PRODUCT_STOCK.getMessage());
        }

        @DisplayName("값이 0 이하이면, INVALID_STOCK 에러가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = {0L, -1L, -100L, -999L})
        void throwsException_whenNotPositive(Long value) {
            assertThatThrownBy(() -> Stock.init(value))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining(ErrorType.INVALID_STOCK.getMessage());
        }
    }

    @DisplayName("재고를 차감할 때,")
    @Nested
    class Deduct {

        @DisplayName("요청 수량이 재고보다 적으면, 수량만큼 차감된다.")
        @Test
        void deductsStock_whenSufficientStock() {
            // arrange
            var stock = Stock.init(10L);

            // act
            stock.deduct(3L);

            // assert
            assertThat(stock).isEqualTo(new Stock(7L));
        }

        @DisplayName("요청 수량이 재고와 같으면, 재고가 0이 된다.")
        @Test
        void deductsToZero_whenExactQuantity() {
            // arrange
            var stock = Stock.init(5L);

            // act
            stock.deduct(5L);

            // assert
            assertThat(stock).isEqualTo(new Stock(0L));
        }

        @DisplayName("재고가 0이면, SOLD_OUT_PRODUCT 에러가 발생한다.")
        @Test
        void throwsException_whenSoldOut() {
            // arrange
            var stock = Stock.init(5L);
            stock.deduct(5L);

            // act & assert
            assertThatThrownBy(() -> stock.deduct(1L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining(ErrorType.SOLD_OUT_PRODUCT.getMessage());
        }

        @DisplayName("요청 수량이 재고보다 많으면, INSUFFICIENT_STOCK 에러가 발생한다.")
        @Test
        void throwsException_whenInsufficientStock() {
            // arrange
            var stock = Stock.init(3L);

            // act & assert
            assertThatThrownBy(() -> stock.deduct(5L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining(ErrorType.INSUFFICIENT_STOCK.getMessage());
        }
    }
}
