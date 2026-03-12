package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockTest {

    @DisplayName("Stock을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 수량으로 생성하면, 정상적으로 생성된다.")
        @Test
        void createsStock_whenQuantityIsValid() {
            // arrange
            int quantity = 100;

            // act
            Stock stock = new Stock(quantity);

            // assert
            assertThat(stock.quantity()).isEqualTo(100);
        }

        @DisplayName("수량이 0이면, 정상적으로 생성된다.")
        @Test
        void createsStock_whenQuantityIsZero() {
            // arrange
            int quantity = 0;

            // act
            Stock stock = new Stock(quantity);

            // assert
            assertThat(stock.quantity()).isEqualTo(0);
        }

        @DisplayName("수량이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenQuantityIsNegative() {
            // arrange
            int quantity = -1;

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new Stock(quantity);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 차감할 때,")
    @Nested
    class Decrease {

        @DisplayName("충분한 재고가 있으면, 차감된 Stock을 반환한다.")
        @Test
        void returnsDecreasedStock_whenStockIsSufficient() {
            // arrange
            Stock stock = new Stock(100);

            // act
            Stock result = stock.decrease(30);

            // assert
            assertThat(result.quantity()).isEqualTo(70);
        }

        @DisplayName("재고와 정확히 같은 수량을 차감하면, 0이 된다.")
        @Test
        void returnsZeroStock_whenDecreasingExactAmount() {
            // arrange
            Stock stock = new Stock(50);

            // act
            Stock result = stock.decrease(50);

            // assert
            assertThat(result.quantity()).isEqualTo(0);
        }

        @DisplayName("재고가 부족하면, INSUFFICIENT_STOCK 예외가 발생한다.")
        @Test
        void throwsInsufficientStockException_whenStockIsInsufficient() {
            // arrange
            Stock stock = new Stock(10);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                stock.decrease(20);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.INSUFFICIENT_STOCK);
        }

        @DisplayName("차감 수량이 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenAmountIsZero() {
            // arrange
            Stock stock = new Stock(100);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                stock.decrease(0);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("차감 수량이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenAmountIsNegative() {
            // arrange
            Stock stock = new Stock(100);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                stock.decrease(-10);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("원본 Stock은 변경되지 않는다. (불변성)")
        @Test
        void originalStockRemainsUnchanged() {
            // arrange
            Stock original = new Stock(100);

            // act
            Stock decreased = original.decrease(30);

            // assert
            assertThat(original.quantity()).isEqualTo(100);
            assertThat(decreased.quantity()).isEqualTo(70);
        }
    }

    @DisplayName("재고를 증가시킬 때,")
    @Nested
    class Increase {

        @DisplayName("유효한 수량으로 증가시키면, 증가된 Stock을 반환한다.")
        @Test
        void returnsIncreasedStock_whenAmountIsValid() {
            // arrange
            Stock stock = new Stock(100);

            // act
            Stock result = stock.increase(50);

            // assert
            assertThat(result.quantity()).isEqualTo(150);
        }

        @DisplayName("증가 수량이 0이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenAmountIsZero() {
            // arrange
            Stock stock = new Stock(100);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                stock.increase(0);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("증가 수량이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenAmountIsNegative() {
            // arrange
            Stock stock = new Stock(100);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                stock.increase(-10);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
