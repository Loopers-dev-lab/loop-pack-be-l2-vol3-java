package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockTest {

    @DisplayName("Stock을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("0 이상이면, 정상 생성된다.")
        @Test
        void createsStock_whenQuantityIsZeroOrPositive() {
            Stock stock = new Stock(10);
            assertThat(stock.quantity()).isEqualTo(10);
        }

        @DisplayName("0이면, 정상 생성된다.")
        @Test
        void createsStock_whenQuantityIsZero() {
            Stock stock = new Stock(0);
            assertThat(stock.quantity()).isEqualTo(0);
        }

        @DisplayName("음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNegative() {
            CoreException result = assertThrows(CoreException.class, () -> new Stock(-1));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 차감할 때, ")
    @Nested
    class Deduct {

        @DisplayName("충분한 재고가 있으면, 차감된 Stock을 반환한다.")
        @Test
        void returnsDeductedStock_whenSufficient() {
            Stock stock = new Stock(10);
            Stock result = stock.deduct(3);
            assertThat(result.quantity()).isEqualTo(7);
        }

        @DisplayName("재고가 부족하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenInsufficient() {
            Stock stock = new Stock(2);
            CoreException result = assertThrows(CoreException.class, () -> stock.deduct(3));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("재고와 동일한 수량이면, 0이 된다.")
        @Test
        void returnsZero_whenExactAmount() {
            Stock stock = new Stock(5);
            Stock result = stock.deduct(5);
            assertThat(result.quantity()).isEqualTo(0);
        }
    }
}
