package com.loopers.domain.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
            var stock = new Stock(value);

            // assert
            assertThat(stock.getValue()).isEqualTo(value);
        }

        @DisplayName("값이 null이면, REQUIRED_PRODUCT_STOCK 에러가 발생한다.")
        @ParameterizedTest
        @NullSource
        void throwsException_whenNull(Long value) {
            assertThatThrownBy(() -> new Stock(value))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining(ErrorType.REQUIRED_PRODUCT_STOCK.getMessage());
        }

        @DisplayName("값이 0 이하이면, INVALID_STOCK 에러가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = {0L, -1L, -100L, -999L})
        void throwsException_whenNotPositive(Long value) {
            assertThatThrownBy(() -> new Stock(value))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining(ErrorType.INVALID_STOCK.getMessage());
        }
    }
}