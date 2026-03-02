package com.loopers.domain.product.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class StockTest {

    @DisplayName("Stock을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 값이면 정상 생성된다")
        @Test
        void success() {
            Stock stock = assertDoesNotThrow(() -> new Stock(10));
            assertThat(stock.value()).isEqualTo(10);
        }

        @DisplayName("0이면 정상 생성된다")
        @Test
        void success_whenZero() {
            Stock stock = assertDoesNotThrow(() -> new Stock(0));
            assertThat(stock.value()).isEqualTo(0);
        }

        @DisplayName("음수이면 예외가 발생한다")
        @Test
        void throwsException_whenNegative() {
            assertThatThrownBy(() -> new Stock(-1))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @DisplayName("Stock을 감소시킬 때, ")
    @Nested
    class Decrease {

        @DisplayName("재고보다 적은 수량이면 감소된 재고가 반환된다")
        @Test
        void success() {
            Stock stock = new Stock(10);
            Stock result = stock.decrease(3);
            assertThat(result.value()).isEqualTo(7);
        }

        @DisplayName("재고와 동일한 수량이면 0이 반환된다")
        @Test
        void success_whenExactAmount() {
            Stock stock = new Stock(5);
            Stock result = stock.decrease(5);
            assertThat(result.value()).isEqualTo(0);
        }

        @DisplayName("재고보다 많은 수량이면 예외가 발생한다")
        @Test
        void throwsException_whenInsufficientStock() {
            assertThatThrownBy(() -> new Stock(3).decrease(5))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @DisplayName("Stock을 증가시킬 때, ")
    @Nested
    class Increase {

        @DisplayName("수량을 더하면 증가된 재고가 반환된다")
        @Test
        void success() {
            Stock stock = new Stock(10);
            Stock result = stock.increase(5);
            assertThat(result.value()).isEqualTo(15);
        }
    }
}
